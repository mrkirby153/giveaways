import { makeApiRequest } from "@/modules/botApi";
import { cookies } from "next/headers";

export async function GET(request: Request) {
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
  return new Response(await response.text());
}
