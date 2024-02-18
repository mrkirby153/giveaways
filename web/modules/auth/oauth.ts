import { SignJWT, jwtVerify } from "jose";

export interface User {
  id: string;
}

export const verifyJWT = async <T>(token: string): Promise<T> => {
  try {
    return (
      await jwtVerify(token, new TextEncoder().encode(process.env.JWT_SECRET))
    ).payload as T;
  } catch (error) {
    throw new Error("Invalid token");
  }
};

export const signJWT = async (
  payload: { sub: string },
  options: { exp: string }
) => {
  const secret = new TextEncoder().encode(process.env.JWT_SECRET);
  const alg = "HS256";
  return new SignJWT(payload)
    .setProtectedHeader({ alg })
    .setIssuedAt()
    .setExpirationTime(options.exp)
    .sign(secret);
};
