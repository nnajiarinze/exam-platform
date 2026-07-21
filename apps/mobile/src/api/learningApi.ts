import { client } from './generated/client.gen';
import { createMockExam, createPracticeSession, deleteMyLearnerAccount, flagMockExamQuestion, getMockExam, getMockExamConfiguration, getMockExamHistory,
  getMockExamQuestion, getMockExamResults, getNextPracticeQuestion, getSubjects, getTopicProgress,
  getMyLearnerProfile, submitMockExam, submitMockExamResponse, submitPracticeResponse } from './generated/sdk.gen';
import type { CreatePracticeSessionRequest, SubmitAnswerRequest } from './generated/types.gen';
import { appConfig } from './config';
import { authenticationExpired, validAccessToken } from '../features/auth/authTokenStore';

client.setConfig({ baseUrl: appConfig.learningBaseUrl, throwOnError: true });
client.interceptors.request.use(async (request) => {
  const token = await validAccessToken();
  if (token) request.headers.set('Authorization', `Bearer ${token}`);
  return request;
});
client.interceptors.response.use(async (response, request) => {
  if (__DEV__ && !response.ok) {
    const details = await response.clone().text();
    console.error(`[Learning Service] ${request.method} ${request.url} -> ${response.status}`, details);
  }
  if (response.status === 401) authenticationExpired();
  return response;
});

const headers = (identity: string) => appConfig.defaultLearnerIdentity && identity === appConfig.defaultLearnerIdentity ? ({ 'X-Learner-Identity': identity }) : undefined;

export const learningApi = {
  profile: async (identity: string) => (await getMyLearnerProfile({ headers: headers(identity), throwOnError: true })).data,
  deleteAccount: async (identity: string) => (await deleteMyLearnerAccount({ headers: headers(identity), throwOnError: true })).data,
  subjects: async (identity: string) => (await getSubjects({ query: { examId: appConfig.examId }, headers: headers(identity), throwOnError: true })).data,
  createSession: async (identity: string, body: CreatePracticeSessionRequest) => (await createPracticeSession({ body, headers: headers(identity), throwOnError: true })).data,
  nextQuestion: async (identity: string, sessionId: string) => (await getNextPracticeQuestion({ path: { sessionId }, headers: headers(identity), throwOnError: true })).data,
  submitAnswer: async (identity: string, sessionId: string, body: SubmitAnswerRequest) => (await submitPracticeResponse({ path: { sessionId }, body, headers: headers(identity), throwOnError: true })).data,
  progress: async (identity: string) => (await getTopicProgress({ headers: headers(identity), throwOnError: true })).data,
  createMockExam: async (identity: string) => (await createMockExam({ body: { examId: appConfig.examId }, headers: headers(identity), throwOnError: true })).data,
  mockExamConfiguration: async () => (await getMockExamConfiguration({ query: { examId: appConfig.examId }, throwOnError: true })).data,
  mockExam: async (identity: string, attemptId: string) => (await getMockExam({ path: { attemptId }, headers: headers(identity), throwOnError: true })).data,
  mockQuestion: async (identity: string, attemptId: string, sequenceNumber?: number) => (await getMockExamQuestion({ path: { attemptId }, query: { sequenceNumber }, headers: headers(identity), throwOnError: true })).data,
  answerMockQuestion: async (identity: string, attemptId: string, attemptQuestionId: string, selectedOptionIds: string[], version?: number) => (await submitMockExamResponse({ path: { attemptId }, body: { attemptQuestionId, selectedOptionIds, version }, headers: headers(identity), throwOnError: true })).data,
  flagMockQuestion: async (identity: string, attemptId: string, attemptQuestionId: string, flagged: boolean, version?: number) => (await flagMockExamQuestion({ path: { attemptId, attemptQuestionId }, body: { flagged, version }, headers: headers(identity), throwOnError: true })).data,
  submitMockExam: async (identity: string, attemptId: string) => (await submitMockExam({ path: { attemptId }, headers: headers(identity), throwOnError: true })).data,
  mockResults: async (identity: string, attemptId: string) => (await getMockExamResults({ path: { attemptId }, headers: headers(identity), throwOnError: true })).data,
  mockHistory: async (identity: string) => (await getMockExamHistory({ headers: headers(identity), throwOnError: true })).data,
};
