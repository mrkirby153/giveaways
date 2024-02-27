import { JWTPayload, SignJWT, jwtVerify } from "jose";
import invariant from "tiny-invariant";

export interface User {
  id: string;
  username: string;
  discriminator: string;
  global_name: string;
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
  payload: JWTPayload,
  options: { exp: string }
) => {
  invariant(payload.sub, "Subject is required");
  const secret = new TextEncoder().encode(process.env.JWT_SECRET);
  const alg = "HS256";
  return new SignJWT(payload)
    .setProtectedHeader({ alg })
    .setIssuedAt()
    .setExpirationTime(options.exp)
    .setSubject(payload.sub)
    .sign(secret);
};
