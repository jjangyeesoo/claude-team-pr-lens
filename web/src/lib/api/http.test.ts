import { describe, expect, it } from "vitest";
import { ApiError, apiUrl, parseResponse } from "./http";

describe("apiUrl", () => {
  it("joinsWithExactlyOneSlash", () => {
    expect(apiUrl("http://localhost:8080/", "/api/v1/memos")).toBe(
      "http://localhost:8080/api/v1/memos",
    );
    expect(apiUrl("http://localhost:8080", "api/v1/memos")).toBe(
      "http://localhost:8080/api/v1/memos",
    );
  });

  it("collapsesMultipleTrailingSlashesInBaseUrl", () => {
    expect(apiUrl("http://localhost:8080///", "/api/v1/memos")).toBe(
      "http://localhost:8080/api/v1/memos",
    );
  });
});

describe("parseResponse", () => {
  it("returnsJsonBodyOnSuccess", async () => {
    const res = Response.json([{ id: 1, title: "t" }]);
    await expect(parseResponse(res)).resolves.toEqual([{ id: 1, title: "t" }]);
  });

  it("throwsApiErrorWithStandardErrorBody", async () => {
    const body = {
      code: "MEMO_NOT_FOUND",
      message: "메모가 없습니다",
      details: [],
    };
    const res = Response.json(body, { status: 404 });

    const error = await parseResponse(res).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 404, body });
  });

  it("throwsApiErrorWithNullBodyWhenErrorIsNotJson", async () => {
    const res = new Response("<html>Bad Gateway</html>", { status: 502 });

    const error = await parseResponse(res).catch((e: unknown) => e);

    expect(error).toMatchObject({
      status: 502,
      body: null,
      message: "HTTP 502",
    });
  });
});
