import type {
  ApiResult,
  CurrentUser,
  Device,
  DeviceInput,
  DevicePage,
} from "./types";

const API_BASE =
  process.env.NEXT_PUBLIC_API_BASE_URL?.replace(/\/$/, "") || "/backend";

export class ApiError extends Error {
  status: number;
  code?: number;

  constructor(message: string, status: number, code?: number) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
  }
}

function accessToken() {
  if (typeof window === "undefined") return "";
  return localStorage.getItem("equipops_access_token") || "";
}

async function request<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const token = accessToken();
  const headers = new Headers(init.headers);
  if (!(init.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }
  if (token) headers.set("Authorization", `Bearer ${token}`);

  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers,
  });

  const contentType = response.headers.get("content-type") || "";
  const payload = contentType.includes("application/json")
    ? ((await response.json()) as ApiResult<T>)
    : null;

  if (!response.ok || (payload && payload.code !== 0)) {
    throw new ApiError(
      payload?.msg || `请求失败（HTTP ${response.status}）`,
      response.status,
      payload?.code,
    );
  }

  if (!payload) {
    throw new ApiError("接口未返回 JSON 数据", response.status);
  }
  return payload.data;
}

export function hasStoredSession() {
  return typeof window !== "undefined" && Boolean(accessToken());
}

export async function login(username: string, password: string) {
  const token = await request<{
    accessToken: string;
    tokenType: string;
    expiresIn: number;
  }>("/auth/login", {
    method: "POST",
    body: JSON.stringify({ username, password }),
  });
  localStorage.setItem("equipops_access_token", token.accessToken);
  return token;
}

export async function register(input: {
  username: string;
  password: string;
  realName: string;
  deptId: number;
  phone: string;
  email?: string;
}) {
  return request<{
    accessToken: string;
    tokenType: string;
    expiresIn: number;
  }>("/auth/register", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function getCurrentUser() {
  return request<CurrentUser>("/auth/me");
}

export function getDevicePage(params: {
  pageNum?: number;
  pageSize?: number;
  name?: string;
  code?: string;
  status?: string;
}) {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== "") query.set(key, String(value));
  }
  return request<DevicePage>(`/devices/page?${query.toString()}`);
}

export function getDevice(id: number) {
  return request<Device>(`/devices/${id}`);
}

export function createDevice(input: DeviceInput) {
  return request<Device>("/devices", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function updateDevice(id: number, input: Omit<DeviceInput, "code">) {
  return request<Device>(`/devices/${id}`, {
    method: "PUT",
    body: JSON.stringify(input),
  });
}

export function deleteDevice(id: number) {
  return request<void>(`/devices/${id}`, { method: "DELETE" });
}
