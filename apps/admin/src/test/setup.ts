import '@testing-library/jest-dom/vitest';
import { afterEach, vi } from 'vitest';
import { fetch, Headers, Request, Response } from 'undici';

Object.assign(globalThis, { fetch, Headers, Request, Response });
afterEach(() => { sessionStorage.clear(); vi.unstubAllGlobals(); });
