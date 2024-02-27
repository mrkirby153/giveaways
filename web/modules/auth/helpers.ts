import { cache } from "react";
import { User, verifyJWT } from "./oauth";
import { cookies, headers } from "next/headers";
import type { ErrorKey } from "./errors";
import { NextResponse, NextRequest } from "next/server";
import { redirect } from "@/utils/nextUrlUtils";
import invariant from "tiny-invariant";

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

  // let verifiedToken = try {
  //   await verifyJWT<{ sub: string }>(token)
  // } catch {
  //   return null;
  // }
  // return {
  //   id: subject,
  // };
  try {
    const verifiedToken = await verifyJWT<{ sub: string; user: User }>(token);
    invariant(
      verifiedToken.sub == verifiedToken.user.id,
      "Subject and user id must match"
    );
    return verifiedToken.user;
  } catch {
    return null;
  }
});

export function getErrorResponse(request: NextRequest, error: ErrorKey) {
  const newUrl = redirect(request.nextUrl, {
    to: "/oauth/error",
    params: { error },
  });
  return NextResponse.redirect(newUrl);
}
