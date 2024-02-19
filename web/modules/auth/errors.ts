type ErrorType = {
  title: string;
  description: string;
};

type Errors = {
  [key: string]: ErrorType;
};

export const errors = {
  ACCESS_DENIED: {
    title: "Access Denied",
    description: "You denied access to the application.",
  },
  UNKNOWN_ERROR: {
    title: "An Unknown Error Occurred",
    description: "An unknown error occurred.",
  },
  INVALID_STATE: {
    title: "State Mismatch",
    description:
      "The state returned does not match the expected state. This could be because you tried to log in from a different tab or window.",
  },
  INVALID_TOKEN: {
    title: "Unauthorized",
    description: "The authroization we received was invalid.",
  },
} satisfies Errors;

export type ErrorKey = keyof typeof errors;
