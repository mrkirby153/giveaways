import { NextURL } from "next/dist/server/web/next-url";

type RedirectProperties = {
  to: string;
  params?: Record<string, string>;
};

/**
 * Clears the search params from a URL
 * @param url The URL to clear the search params from
 * @param clone If the URL should be cloned before modifying
 * @returns The URL with the search params cleared. If `clone` is `true`, a new URL is returned, otherwise the original URL is modified.
 */
export function clearSearchParams(
  url: NextURL,
  clone: boolean = true
): NextURL {
  if (clone) {
    url = url.clone();
  }
  let toDelete: string[] = [];
  url.searchParams.forEach((_, k) => {
    toDelete.push(k);
  });
  toDelete.forEach((k) => url.searchParams.delete(k));
  return url;
}

/**
 * Builds a new URL to the given path
 * @param url The original URL to modify
 * @param properties The properties to modify the URL with
 * @returns The new URL
 */
export function redirect(
  url: NextURL,
  { to, params }: RedirectProperties
): NextURL {
  let newUrl = url.clone();
  newUrl.pathname = to;
  if (params) {
    clearSearchParams(newUrl, false);
    for (let [key, value] of Object.entries(params)) {
      newUrl.searchParams.set(key, value);
    }
  }
  return newUrl;
}
