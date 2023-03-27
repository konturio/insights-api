package io.kontur.insightsapi.service;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import io.kontur.insightsapi.dto.BivariateIndicatorDto;
import io.kontur.insightsapi.dto.FileUploadResultDto;
import io.kontur.insightsapi.exception.BivariateIndicatorsPRViolationException;
import io.kontur.insightsapi.repository.IndicatorRepository;
import io.kontur.insightsapi.service.auth.AuthService;
import lombok.AllArgsConstructor;
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import javax.validation.*;
import java.io.IOException;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
@AllArgsConstructor
public class IndicatorService {

    private static final Logger logger = LoggerFactory.getLogger(IndicatorService.class);

    private final IndicatorRepository indicatorRepository;

    private final ServletFileUpload upload;

    private final ObjectMapper objectMapper;

    private final AuthService authService;

    @Transactional
    public ResponseEntity<String> uploadIndicatorData(HttpServletRequest request) {
        String uuid = "";
        FileUploadResultDto fileUploadResultDto = new FileUploadResultDto();
        boolean update = false;

        try {

            BivariateIndicatorDto incomingBivariateIndicatorDto;
            FileItemIterator itemIterator = upload.getItemIterator(request);
            int itemIndex = 0;

            while (itemIterator.hasNext()) {
                FileItemStream item = itemIterator.next();
                String name = item.getFieldName();

                if (!item.isFormField() && "file".equals(name) && itemIndex == 1) {

                    fileUploadResultDto = indicatorRepository.uploadCSVFileIntoTempTable(item);

                } else if ("parameters".equals(name) && itemIndex == 0) {

                    incomingBivariateIndicatorDto = parseRequestFormDataParameters(item);
                    validateParameters(incomingBivariateIndicatorDto);

                    String owner = authService.getCurrentUsername().orElseThrow();
                    BivariateIndicatorDto savedBivariateIndicator =
                            indicatorRepository.getIndicatorByIdAndOwner(incomingBivariateIndicatorDto.getId(), owner);

                    if (savedBivariateIndicator != null) {
                        update = true;
                    }

                    //TODO: here probably make a 'state' update inside inner transaction so new state being committed
                    uuid = indicatorRepository.createOrUpdateIndicator(incomingBivariateIndicatorDto, owner, update);

                    itemIndex++;

                } else {
                    return logAndReturnErrorWithMessage(400, "Wrong field parameter or wrong parameters order in multipart request: " +
                            "please send a request with multipart data with keys 'parameters' and 'file' in a corresponding order.");
                }
            }

            if (Strings.isNotEmpty(uuid)
                    && Strings.isNotEmpty(fileUploadResultDto.getTempTableName())
                    && fileUploadResultDto.getNumberOfUploadedRows() != 0) {
                return indicatorRepository.copyDataToStatH3(fileUploadResultDto, uuid, update);
            } else if (Strings.isNotEmpty(uuid)
                    && (Strings.isEmpty(fileUploadResultDto.getTempTableName())
                    || fileUploadResultDto.getNumberOfUploadedRows() == 0)) {

                if (Strings.isNotEmpty(fileUploadResultDto.getErrorMessage())) {
                    return logAndReturnErrorWithMessage(400, fileUploadResultDto.getErrorMessage());
                }

                if (fileUploadResultDto.getTempTableName() != null) {
                    indicatorRepository.deleteTempTable(fileUploadResultDto.getTempTableName());
                }

                if (update) {
                    return ResponseEntity.ok().body(uuid);
                }

                indicatorRepository.deleteIndicator(uuid);

                return logAndReturnErrorWithMessage(400, "File was absent or has a missing data in the request");

            } else {
                return logAndReturnErrorWithMessage(500, "Could not process request, neither indicator nor h3 indexes were created");
            }

        } catch (FileUploadException | IOException | ValidationException exception) {
            return logAndReturnErrorWithMessage(400, exception.getMessage());
        } catch (NoSuchElementException exception) {
            return logAndReturnErrorWithMessage(401, "Incorrect authentication data: could not get username");
        } catch (BivariateIndicatorsPRViolationException exception) {
            return logAndReturnErrorWithMessage(500, exception.getMessage());
        } catch (Exception exception) {
            //TODO: update state to previous value if committed in future
            return logAndReturnErrorWithMessage(500, exception.getMessage());
        }
    }

    public void updateIndicatorsLastUpdateDate(Instant lastUpdated) {
        indicatorRepository.updateIndicatorsLastUpdateDate(lastUpdated);
    }

    public Instant getIndicatorsLastUpdateDate() {
        return indicatorRepository.getIndicatorsLastUpdateDate();
    }

    private void validateParameters(BivariateIndicatorDto bivariateIndicatorDto) {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            Set<ConstraintViolation<BivariateIndicatorDto>> validationViolations = validator.validate(bivariateIndicatorDto);
            if (!validationViolations.isEmpty()) {
                StringBuilder validationErrorMessage = new StringBuilder();
                for (ConstraintViolation<BivariateIndicatorDto> bivariateIndicatorDtoConstraintViolation : validationViolations) {
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
            throw new IOException(generateExceptionMessage(exception.getProcessor().getParsingContext().getCurrentName()));
        } catch (MismatchedInputException exception) {
            throw new IOException(generateExceptionMessage(exception.getPath().get(0).getFieldName()));
        }
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

    private ResponseEntity<String> logAndReturnErrorWithMessage(int errorCode, String message) {
        logger.error(message);
        return ResponseEntity.status(errorCode).body(message);
    }
}
