"use client";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { ErrorKey, errors } from "@/modules/auth/errors";

export default function ErrorPage() {
  let query = useSearchParams();
  let error = errors[query.get("error") as ErrorKey];

  let title = error?.title ?? "An Error Occurred";
  let description = error?.description ?? "An unknown error occurred.";

  return (
    <div className="h-screen">
      <div className="flex min-h-full flex-col mt-5">
        <div className="mx-auto w-full max-w-md rounded border border-gray-200 px-8 py-8">
          <h1 className="text-3xl font-bold">{title}</h1>
          <p className="mt-3">{description}</p>
          <p className="mt-3">
            Click{" "}
            <Link href="/oauth/login" className="text-blue-400 hover:underline">
              here
            </Link>{" "}
            to try again.
          </p>
        </div>
      </div>
    </div>
  );
}
