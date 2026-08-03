function log(level: string, message: string): void {
  const timestamp = new Date().toISOString().replace("T", " ").replace("Z", "");
  console.log(`${timestamp} [relay-agent] ${level}: ${message}`);
}

export const logger = {
  info: (message: string): void => log("INFO", message),
  error: (message: string): void => log("ERROR", message),
};
