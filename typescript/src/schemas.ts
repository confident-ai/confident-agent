export enum ResponseMode {
  HTTP_RESPONSE = "HTTP_RESPONSE",
  SSE_STREAMING = "SSE_STREAMING",
  HTTP_STREAMING = "HTTP_STREAMING",
}

export interface RelayRequest {
  id: string;
  method: string;
  url: string;
  headers: Record<string, string>;
  body: unknown;
  timeout: number;
  responseMode: ResponseMode;
}

export enum RelayResponseType {
  RESPONSE = "response",
  ERROR = "error",
  CHUNK = "chunk",
  STREAM_END = "stream_end",
}

export interface RelayResponse {
  id: string;
  type: RelayResponseType;
  statusCode: number;
  headers: Record<string, string>;
  body: string;
}

export interface ToolCall {
  [key: string]: unknown;
  name: string;
  description?: string | null;
  inputParameters?: Record<string, unknown> | null;
  output?: unknown;
}

export interface Turn {
  [key: string]: unknown;
  role: string;
  content: string;
}

export interface Request {
  [key: string]: unknown;
  input: string;
  context: string[];
  retrievalContext: string[];
  expectedOutput: string | null;
  turns: Turn[];
  scenario: string | null;
  state: unknown;
  prompts: Record<string, unknown> | null;
  hyperparameters: Record<string, unknown> | null;
  testCaseId: string | null;
  turnId: string | null;
}

export interface Response {
  output: string;
  retrievalContext?: string[] | null;
  toolsCalled?: ToolCall[] | null;
  state?: unknown;
}

export function parseRelayRequest(raw: string): RelayRequest {
  const data = JSON.parse(raw) as Record<string, unknown>;
  if (
    typeof data.id !== "string" ||
    typeof data.method !== "string" ||
    typeof data.url !== "string"
  ) {
    throw new Error("id, method, and url are required strings");
  }
  return {
    id: data.id,
    method: data.method,
    url: data.url,
    headers: (data.headers as Record<string, string> | undefined) ?? {},
    body: data.body ?? null,
    timeout: (data.timeout as number | undefined) ?? 60,
    responseMode:
      (data.response_mode as ResponseMode | undefined) ?? ResponseMode.HTTP_RESPONSE,
  };
}

export function parseRequest(body: Record<string, unknown>): Request {
  const {
    input = "",
    context = [],
    retrieval_context: retrievalContext = [],
    expected_output: expectedOutput = null,
    turns = [],
    scenario = null,
    state = null,
    prompts = null,
    hyperparameters = null,
    test_case_id: testCaseId = null,
    turn_id: turnId = null,
    ...extra
  } = body;
  return {
    ...extra,
    input: input as string,
    context: context as string[],
    retrievalContext: retrievalContext as string[],
    expectedOutput: expectedOutput as string | null,
    turns: turns as Turn[],
    scenario: scenario as string | null,
    state,
    prompts: prompts as Record<string, unknown> | null,
    hyperparameters: hyperparameters as Record<string, unknown> | null,
    testCaseId: testCaseId as string | null,
    turnId: turnId as string | null,
  };
}

export function serializeResponse(response: Response): string {
  return JSON.stringify({
    output: response.output,
    retrieval_context: response.retrievalContext ?? null,
    tools_called: response.toolsCalled
      ? response.toolsCalled.map(serializeToolCall)
      : null,
    state: response.state ?? null,
  });
}

function serializeToolCall(toolCall: ToolCall): Record<string, unknown> {
  const { name, description, inputParameters, output, ...extra } = toolCall;
  return {
    name,
    description: description ?? null,
    input_parameters: inputParameters ?? null,
    output: output ?? null,
    ...extra,
  };
}
