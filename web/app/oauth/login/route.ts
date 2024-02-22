import { getErrorResponse } from "@/modules/auth/helpers";
import { makeApiRequest } from "@/modules/botApi";
import { NextRequest, NextResponse } from "next/server";

function generateState() {
  return Math.random().toString(36).substring(2);
}

async function getRedirectUrl(callback: string, state: string) {
  let response: Response;
  try {
    response = await makeApiRequest("/auth/auth-url", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        state: state,
        redirectUrl: callback,
      }),
    });
    if (!response.ok) {
      return null;
    }
  } catch (e) {
    return null;
  }
  return response.text();
}

export async function GET(req: NextRequest) {
  const url = req.nextUrl.clone();
  url.pathname = "/oauth/callback";

  const state = generateState();

  const target = await getRedirectUrl(url.toString(), state);
  if (!target) {
    return getErrorResponse(req, "AUTH_URL_FAILED");
  }

  const resp = NextResponse.redirect(target);
  resp.cookies.set("state", state, {
    httpOnly: true,
    expires: new Date(Date.now() + 1000 * 60 * 5),
  });

  return resp;
}
