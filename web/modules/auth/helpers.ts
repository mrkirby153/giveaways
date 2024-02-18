import { cache } from "react";
import { User, verifyJWT } from "./oauth";
import { cookies, headers } from "next/headers";

export const getCurrentUser = cache(async (): Promise<User | null> => {
  let token: string | undefined;
  const cookieStore = cookies();
  if (cookieStore.has("token")) {
    token = cookieStore.get("token")?.value;
  } else {
    const allHeaders = headers();
    token = allHeaders.get("authorization")?.replace("Bearer ", "");
  }
  if (!token) {
    return null;
  }

  let subject = "";
  try {
    subject = (await verifyJWT<{ sub: string }>(token)).sub;
  } catch {
    return null;
  }
  return {
    id: subject,
  };
});
