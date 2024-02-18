import { NextRequest, NextResponse } from "next/server";

export async function GET(req: NextRequest) {
  const url = req.nextUrl.clone();

  url.pathname = "/";
  const response = NextResponse.redirect(url);

  await Promise.all([
    response.cookies.set({
      name: "token",
      value: "",
      maxAge: 0,
    }),
  ]);
  return response;
}
