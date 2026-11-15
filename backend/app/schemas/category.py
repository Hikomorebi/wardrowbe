from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, field_validator


class CategoryWrite(BaseModel):
    name: str = Field(min_length=1, max_length=100)

    @field_validator("name", mode="before")
    @classmethod
    def strip_name(cls, value: object) -> object:
        return value.strip() if isinstance(value, str) else value


class CategoryResponse(CategoryWrite):
    model_config = ConfigDict(from_attributes=True)
    id: UUID
