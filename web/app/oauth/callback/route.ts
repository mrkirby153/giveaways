import { User, signJWT } from "@/modules/auth/oauth";
import { makeApiRequest } from "@/modules/botApi";
import { cookies } from "next/headers";
import { NextRequest, NextResponse } from "next/server";

export async function GET(request: NextRequest) {
  let url = new URL(request.url);

  let allCookies = cookies();

  let state = allCookies.get("state")?.value;
  let receivedState = url.searchParams.get("state");

  if (state !== receivedState) {
    return new Response("Invalid state", { status: 400 });
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

  let json = await response.json();
  let token = json.access_token;
  console.log(json);

  response = await fetch("https://discord.com/api/v10/users/@me", {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
  if (!response.ok) {
    return new Response("Invalid token", { status: 400 });
  }
  let userJson = await response.json();
  console.log(userJson);

  let user: User = {
    id: userJson.id,
  };

  let jwt = await signJWT({ sub: user.id }, { exp: "1h" });

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
