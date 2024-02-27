import { getErrorResponse } from "@/modules/auth/helpers";
import { User, signJWT } from "@/modules/auth/oauth";
import { makeApiRequest } from "@/modules/botApi";
import { cookies } from "next/headers";
import { NextRequest, NextResponse } from "next/server";

function handleError(request: NextRequest, error: string) {
  switch (error) {
    case "access_denied":
      return getErrorResponse(request, "ACCESS_DENIED");
    default:
      return getErrorResponse(request, "UNKNOWN_ERROR");
  }
}

export async function GET(request: NextRequest) {
  let url = new URL(request.url);
  let allCookies = cookies();
  let state = allCookies.get("state")?.value;

  if (url.searchParams.get("error")) {
    return handleError(request, url.searchParams.get("error") as string);
  }

  let receivedState = url.searchParams.get("state");

  if (state !== receivedState) {
    return getErrorResponse(request, "INVALID_STATE");
  }
  let code = url.searchParams.get("code");
  url.searchParams.delete("code");
  url.searchParams.delete("state");

  let response = await makeApiRequest("/auth/validate-token", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      token: code,
      redirectUri: url.toString(),
    }),
  });

  if (!response.ok) {
    return getErrorResponse(request, "TOKEN_VALIDATION_FAILED");
  }

  let json = await response.json();
  let token = json.access_token;

  response = await fetch("https://discord.com/api/v10/users/@me", {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
  if (!response.ok) {
    return getErrorResponse(request, "INVALID_TOKEN");
  }
  let userJson = await response.json();

  let user: User = {
    id: userJson.id,
    username: userJson.username,
    discriminator: userJson.discriminator,
    global_name: userJson.global_name,
  };

  let jwt = await signJWT({ sub: user.id, user }, { exp: "1h" });

  let destination = request.nextUrl.clone();
  destination.pathname = "/";
  destination.searchParams.delete("state");
  destination.searchParams.delete("code");
  let nextResponse = NextResponse.redirect(destination);
  nextResponse.cookies.set("token", jwt, {
    httpOnly: true,
    expires: new Date(Date.now() + 1000 * 60 * 60),
  });
  nextResponse.cookies.set("state", "", {
    maxAge: 0,
  });
  return nextResponse;
}
