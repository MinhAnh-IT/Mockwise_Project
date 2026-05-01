import { request, unwrap } from '@/api/client';
import type {
  AccountRequest,
  LoginResponseData,
  RegisterRequest,
  RegisterResponseData,
  ResetPasswordRequest,
  VerifyAccountOtpRequest,
} from '@/types/auth';

const PREFIX = '/api/v1/iam';

export function signIn(payload: AccountRequest): Promise<LoginResponseData> {
  return unwrap(`${PREFIX}/auth/sign-in`, {
    method: 'POST',
    body: payload,
    auth: false,
  });
}

export function register(payload: RegisterRequest): Promise<RegisterResponseData> {
  return unwrap(`${PREFIX}/users/register`, {
    method: 'POST',
    body: payload,
    auth: false,
  });
}

export function logout(): Promise<void> {
  return request(`${PREFIX}/auth/logout`, { method: 'POST' });
}

export function renewToken(): Promise<LoginResponseData> {
  return unwrap(`${PREFIX}/auth/token/renew`, {
    method: 'POST',
    auth: false,
  });
}

export function sendVerifyOtp(email: string): Promise<boolean> {
  return unwrap(`${PREFIX}/auth/verify-account/send`, {
    method: 'POST',
    body: { email },
    auth: false,
  });
}

export function confirmVerifyOtp(payload: VerifyAccountOtpRequest): Promise<boolean> {
  return unwrap(`${PREFIX}/auth/verify-account/confirm`, {
    method: 'POST',
    body: payload,
    auth: false,
  });
}

export function sendForgotPasswordOtp(email: string): Promise<boolean> {
  return unwrap(`${PREFIX}/auth/forgot-password/send`, {
    method: 'POST',
    body: { email },
    auth: false,
  });
}

export function confirmForgotPasswordOtp(payload: ResetPasswordRequest): Promise<boolean> {
  return unwrap(`${PREFIX}/auth/forgot-password/confirm`, {
    method: 'POST',
    body: payload,
    auth: false,
  });
}
