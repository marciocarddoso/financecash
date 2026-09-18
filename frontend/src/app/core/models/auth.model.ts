export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  name: string;
  email: string;
  password: string;
}

export interface AuthResponse {
  token: string;
  tokenType: string;
  name: string;
  email: string;
}

export interface CurrentUser {
  name: string;
  email: string;
}
