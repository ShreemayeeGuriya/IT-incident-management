import client from "./client";
import type {
  AuthResponse,
  LoginRequest,
  RegisterRequest,
  User,
} from "../types/user";

export async function login(body: LoginRequest): Promise<AuthResponse> {
  const res = await client.post<AuthResponse>("/api/auth/login", body);
  return res.data;
}

export async function register(body: RegisterRequest): Promise<AuthResponse> {
  const res = await client.post<AuthResponse>("/api/auth/register", body);
  return res.data;
}

export async function getMe(): Promise<User> {
  const res = await client.get<User>("/api/auth/me");
  return res.data;
}