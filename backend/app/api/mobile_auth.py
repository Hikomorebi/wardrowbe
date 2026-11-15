from datetime import datetime
from uuid import UUID

from fastapi import APIRouter, Request, Response
from pydantic import BaseModel, Field

from app.api.local_auth import LoginRequest, require_local
from app.database import DbSession
from app.services import mobile_auth
from app.utils.rate_limit import rate_limit_by_ip

router = APIRouter(prefix="/auth/mobile", tags=["Mobile authentication"])


class MobileUser(BaseModel):
    id: UUID
    email: str
    display_name: str


class MobileTokens(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str
    expires_in: int
    session_expires_at: datetime
    user: MobileUser


class RefreshRequest(BaseModel):
    refresh_token: str = Field(min_length=32, max_length=128)


def no_store(response: Response) -> None:
    response.headers["Cache-Control"] = "no-store"
    response.headers["Pragma"] = "no-cache"


@router.post("/login", response_model=MobileTokens)
async def login(data: LoginRequest, request: Request, response: Response, db: DbSession):
    require_local()
    await rate_limit_by_ip(request, "local_login", 20, 60)
    no_store(response)
    return await mobile_auth.login(db, data.email, data.password)


@router.post("/refresh", response_model=MobileTokens)
async def refresh(data: RefreshRequest, request: Request, response: Response, db: DbSession):
    require_local()
    await rate_limit_by_ip(request, "mobile_refresh", 60, 60)
    no_store(response)
    return await mobile_auth.refresh(db, data.refresh_token)


@router.post("/logout", status_code=204)
async def logout(data: RefreshRequest, request: Request, response: Response, db: DbSession):
    require_local()
    await rate_limit_by_ip(request, "mobile_logout", 30, 60)
    no_store(response)
    await mobile_auth.logout(db, data.refresh_token)
