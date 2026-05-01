export type ApiEnvelope<T> = {
  code: number;
  message: string;
  data: T;
};

export type ApiErrorBody = {
  code: number;
  message: string;
};
