import { client } from './generated/client.gen';
import { createMockExam, createPracticeSession, flagMockExamQuestion, getMockExam, getMockExamHistory,
  getMockExamQuestion, getMockExamResults, getNextPracticeQuestion, getSubjects, getTopicProgress,
  submitMockExam, submitMockExamResponse, submitPracticeResponse } from './generated/sdk.gen';
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
  createMockExam: async (identity: string) => (await createMockExam({ body: { examId: appConfig.examId }, headers: headers(identity), throwOnError: true })).data,
  mockExam: async (identity: string, attemptId: string) => (await getMockExam({ path: { attemptId }, headers: headers(identity), throwOnError: true })).data,
  mockQuestion: async (identity: string, attemptId: string, sequenceNumber?: number) => (await getMockExamQuestion({ path: { attemptId }, query: { sequenceNumber }, headers: headers(identity), throwOnError: true })).data,
  answerMockQuestion: async (identity: string, attemptId: string, attemptQuestionId: string, selectedAnswerOptionId: string) => (await submitMockExamResponse({ path: { attemptId }, body: { attemptQuestionId, selectedAnswerOptionId }, headers: headers(identity), throwOnError: true })).data,
  flagMockQuestion: async (identity: string, attemptId: string, attemptQuestionId: string, flagged: boolean) => (await flagMockExamQuestion({ path: { attemptId, attemptQuestionId }, body: { flagged }, headers: headers(identity), throwOnError: true })).data,
  submitMockExam: async (identity: string, attemptId: string) => (await submitMockExam({ path: { attemptId }, headers: headers(identity), throwOnError: true })).data,
  mockResults: async (identity: string, attemptId: string) => (await getMockExamResults({ path: { attemptId }, headers: headers(identity), throwOnError: true })).data,
  mockHistory: async (identity: string) => (await getMockExamHistory({ headers: headers(identity), throwOnError: true })).data,
};
