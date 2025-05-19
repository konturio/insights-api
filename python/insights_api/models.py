from typing import Optional
from pydantic import BaseModel, Field


class AxisOverridesRequest(BaseModel):
    numerator_id: str = Field(..., alias="numerator_id")
    denominator_id: str = Field(..., alias="denominator_id")
    label: Optional[str] = None
    min: Optional[float] = None
    max: Optional[float] = None
    p25: Optional[float] = None
    p75: Optional[float] = None
    min_label: Optional[str] = Field(None, alias="minLabel")
    max_label: Optional[str] = Field(None, alias="maxLabel")
    p25_label: Optional[str] = Field(None, alias="p25Label")
    p75_label: Optional[str] = Field(None, alias="p75Label")

    class Config:
        allow_population_by_field_name = True


class BivariateIndicator(BaseModel):
    id: str
    label: str
    direction: list[list[str]]
    external_id: Optional[str] = Field(None, alias="uuid")
    owner: Optional[str] = None

    class Config:
        allow_population_by_field_name = True
