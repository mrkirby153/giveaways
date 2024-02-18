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

  return new Response("Not found", { status: 404 });
}
