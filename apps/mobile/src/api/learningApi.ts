import { client } from './generated/client.gen';
import { createPracticeSession, getNextPracticeQuestion, getSubjects, getTopicProgress, submitPracticeResponse } from './generated/sdk.gen';
import type { CreatePracticeSessionRequest, SubmitAnswerRequest } from './generated/types.gen';
import { appConfig } from './config';

client.setConfig({ baseUrl: appConfig.learningBaseUrl, throwOnError: true });
client.interceptors.response.use(async (response, request) => {
  if (__DEV__ && !response.ok) {
    const details = await response.clone().text();
    console.error(`[Learning Service] ${request.method} ${request.url} -> ${response.status}`, details);
  }
  return response;
});

const headers = (identity: string) => ({ 'X-Learner-Identity': identity });

export const learningApi = {
  subjects: async (identity: string) => (await getSubjects({ query: { examId: appConfig.examId }, headers: headers(identity), throwOnError: true })).data,
  createSession: async (identity: string, body: CreatePracticeSessionRequest) => (await createPracticeSession({ body, headers: headers(identity), throwOnError: true })).data,
  nextQuestion: async (identity: string, sessionId: string) => (await getNextPracticeQuestion({ path: { sessionId }, headers: headers(identity), throwOnError: true })).data,
  submitAnswer: async (identity: string, sessionId: string, body: SubmitAnswerRequest) => (await submitPracticeResponse({ path: { sessionId }, body, headers: headers(identity), throwOnError: true })).data,
  progress: async (identity: string) => (await getTopicProgress({ headers: headers(identity), throwOnError: true })).data,
};
