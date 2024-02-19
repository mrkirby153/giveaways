import { cache } from "react";
import { User, verifyJWT } from "./oauth";
import { cookies, headers } from "next/headers";
import type { ErrorKey } from "./errors";
import { NextResponse, NextRequest } from "next/server";

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

export function getErrorResponse(request: NextRequest, error: ErrorKey) {
  const newUrl = request.nextUrl.clone();
  newUrl.pathname = "/oauth/error";
  let toDelete: string[] = [];
  newUrl.searchParams.forEach((_, k) => {
    toDelete.push(k);
  });
  toDelete.forEach((k) => newUrl.searchParams.delete(k));
  newUrl.searchParams.set("error", error);
  return NextResponse.redirect(newUrl);
}
