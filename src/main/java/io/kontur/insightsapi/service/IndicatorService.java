package io.kontur.insightsapi.service;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import io.kontur.insightsapi.dto.BivariateIndicatorDto;
import io.kontur.insightsapi.dto.IndicatorState;
import io.kontur.insightsapi.exception.BivariateIndicatorsPRViolationException;
import io.kontur.insightsapi.exception.IndicatorDataProcessingException;
import io.kontur.insightsapi.repository.IndicatorRepository;
import io.kontur.insightsapi.service.auth.AuthService;
import lombok.AllArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PreDestroy;
import javax.servlet.http.HttpServletRequest;
import javax.validation.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
@AllArgsConstructor
public class IndicatorService {

    private static final Logger logger = LoggerFactory.getLogger(IndicatorService.class);

    private final IndicatorRepository indicatorRepository;

    private final ServletFileUpload upload;

    private final ObjectMapper objectMapper;

    private final AuthService authService;

    private static final int CORE_POOL_SIZE = 100;

    private static final int MAX_POOL_SIZE = 150;

    private static final int MAX_QUEUE_SIZE = 200;

    private static final ThreadPoolExecutor uploadExecutor = new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE,
            60, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(MAX_QUEUE_SIZE));

    @Transactional
    public ResponseEntity<String> uploadIndicatorData(HttpServletRequest request) {
        String uuid = "";
        boolean update = false;

        try {
            BivariateIndicatorDto incomingBivariateIndicatorDto;
            FileItemIterator itemIterator = upload.getItemIterator(request);
            int itemIndex = 0;

            while (itemIterator.hasNext()) {
                FileItemStream item = itemIterator.next();
                String name = item.getFieldName();

                if ("parameters".equals(name) && itemIndex == 0) {
                    incomingBivariateIndicatorDto = parseRequestFormDataParameters(item);
                    validateParameters(incomingBivariateIndicatorDto);

                    String owner = authService.getCurrentUsername().orElseThrow();
                    BivariateIndicatorDto savedBivariateIndicator =
                            indicatorRepository.getIndicatorByIdAndOwner(incomingBivariateIndicatorDto.getId(), owner);
                    if (savedBivariateIndicator != null) {
                        update = true;
                    }

                    uuid = indicatorRepository.createOrUpdateIndicator(incomingBivariateIndicatorDto, owner, update);
                    itemIndex++;
                } else if (!item.isFormField() && "file".equals(name) && itemIndex == 1) {
                    if (Strings.isNotEmpty(uuid)) {
                        processAndUploadCsvFile(item, uuid, update);
                        return ResponseEntity.ok().body(uuid);
                    }
                } else {
                    return logAndReturnErrorWithMessage(HttpStatus.BAD_REQUEST, "Wrong field parameter or " +
                            "wrong parameters order in multipart request: please send a request with multipart data " +
                            "with keys 'parameters' and 'file' in a corresponding order");
                }
            }

            return logAndReturnErrorWithMessage(HttpStatus.BAD_REQUEST,
                    "Could not process request, neither indicator nor h3 indexes were created");
        } catch (FileUploadException | IOException | ValidationException exception) {
            return logAndReturnErrorWithMessage(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (NoSuchElementException exception) {
            return logAndReturnErrorWithMessage(HttpStatus.UNAUTHORIZED,
                    "Incorrect authentication data: could not get username");
        } catch (BivariateIndicatorsPRViolationException exception) {
            return logAndReturnErrorWithMessage(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage());
        } catch (IndicatorDataProcessingException exception) {
            if (!update) {
                indicatorRepository.deleteIndicator(uuid);
            }
            return logAndReturnErrorWithMessage(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (Exception exception) {
            if (update) {
                return logAndReturnErrorWithMessage(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Could not update indicator");
            } else {
                indicatorRepository.deleteIndicator(uuid);
                return logAndReturnErrorWithMessage(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Could not process request, neither indicator nor h3 indexes were created");
            }
        }
    }

    public BivariateIndicatorDto getIndicatorByUuid(String uuid) {
        return indicatorRepository.getIndicatorByUuid(uuid);
    }

    public void updateIndicatorsLastUpdateDate(Instant lastUpdated) {
        indicatorRepository.updateIndicatorsLastUpdateDate(lastUpdated);
    }

    public Instant getIndicatorsLastUpdateDate() {
        return indicatorRepository.getIndicatorsLastUpdateDate();
    }

    public void updateIndicatorState(String uuid, IndicatorState state) {
        indicatorRepository.updateIndicatorState(uuid, state);
    }

    @Transactional
    @Scheduled(cron = "0 0 18 * * ?")
    @SchedulerLock(name = "IndicatorService_updateStatH3Geom", lockAtLeastFor = "PT5M", lockAtMostFor = "PT6H")
    public void updateStatH3Geom() {
        try {
            logger.info("Start geometry update job");
            Instant executionStartTime = Instant.now();

            indicatorRepository.updateStatH3Geom();

            Instant executionEndTime = Instant.now();
            Duration executionTime = Duration.between(executionStartTime, executionEndTime);
            logger.info("Geometry update job has been executed successfully and took {}",
                    String.format("%d hours %02d minutes %02d seconds",
                            executionTime.toHours(), executionTime.toMinutesPart(), executionTime.toSecondsPart()));
        } catch (Exception e) {
            logger.error("Error executing geometry update job", e);
        }
    }

    private void validateParameters(BivariateIndicatorDto bivariateIndicatorDto) {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            Set<ConstraintViolation<BivariateIndicatorDto>> validationViolations =
                    validator.validate(bivariateIndicatorDto);
            if (!validationViolations.isEmpty()) {
                StringBuilder validationErrorMessage = new StringBuilder();
                for (ConstraintViolation<BivariateIndicatorDto> bivariateIndicatorDtoConstraintViolation :
                        validationViolations) {
                    validationErrorMessage.append(bivariateIndicatorDtoConstraintViolation.getMessage()).append(". ");
                }
                throw new ValidationException(validationErrorMessage.toString());
            }
        }
    }

    private BivariateIndicatorDto parseRequestFormDataParameters(FileItemStream item) throws IOException {
        try {
            return objectMapper.readValue(item.openStream(), BivariateIndicatorDto.class);
        } catch (JsonParseException exception) {
            throw new IOException(generateExceptionMessage(exception.getProcessor().getParsingContext()
                    .getCurrentName()));
        } catch (MismatchedInputException exception) {
            throw new IOException(generateExceptionMessage(exception.getPath().get(0).getFieldName()));
        }
    }

    private void processAndUploadCsvFile(FileItemStream file, String uuid, boolean update) throws IOException {
        PipedInputStream pipedInputStream = new PipedInputStream();
        PipedOutputStream pipedOutputStream = new PipedOutputStream(pipedInputStream);

        uploadExecutor.submit(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.openStream(),
                    StandardCharsets.UTF_8));
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(pipedOutputStream,
                         StandardCharsets.UTF_8))) {
                String row;
                while ((row = reader.readLine()) != null) {
                    String[] rowValues = row.split(",");
                    writer.write(String.join(",", rowValues[0], uuid, rowValues[1]));
                    writer.newLine();
                }
            } catch (IOException e) {
                throw new IndicatorDataProcessingException("Unable to adjust incoming csv stream with uuid", e);
            }
        });

        indicatorRepository.uploadCsvFileIntoStatH3Table(pipedInputStream, uuid, update);
    }

    private String generateExceptionMessage(String fieldName) {
        if (fieldName == null) {
            return "Incorrect parameters json";
        }
        return switch (fieldName) {
            case "isPublic", "isBase" -> String.format("%s field supports only boolean values", fieldName);
            case "id", "label" -> String.format("%s field supports only string values", fieldName);
            case "copyrights", "allowedUsers" ->
                    String.format("Incorrect type of %s field, array expected.", fieldName);
            case "direction" -> String.format("Incorrect type of %s field, array of arrays expected.", fieldName);
            default -> String.format("Incorrect type of %s field", fieldName);
        };
    }

    private ResponseEntity<String> logAndReturnErrorWithMessage(HttpStatus status, String message) {
        logger.error(message);
        return ResponseEntity.status(status).body(message);
    }

    @PreDestroy
    public void shutdown() {
        uploadExecutor.shutdown();
    }
}
