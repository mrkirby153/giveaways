/**
 * Makes a request to the bot's API. This should only be called on the server.
 * @param endpoint The endpoint to request
 * @param init The parameters for the request
 * @returns The request
 */
export async function makeApiRequest(
  endpoint: string,
  init: RequestInit | undefined = undefined
) {
  let apiEndpoint = process.env.API_ENDPOINT;
  if (!apiEndpoint) {
    throw new Error("API_ENDPOINT is not defined");
  }
  if (!endpoint.startsWith("/")) {
    throw new Error("Endpoint must start with /");
  }
  return fetch(`${apiEndpoint}${endpoint}`, init);
}
