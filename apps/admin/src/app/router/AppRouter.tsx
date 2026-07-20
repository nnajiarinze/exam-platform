import { Navigate, Route, Routes } from 'react-router-dom';
import { ProtectedRoute } from '../auth/ProtectedRoute';
import { AdminLayout } from '../../components/AdminLayout';
import { DashboardPage } from '../../features/dashboard/DashboardPage';
import { LoginPage } from '../../features/auth/LoginPage';
import { UnauthorizedPage } from '../../features/errors/UnauthorizedPage';
import { NotFoundPage } from '../../features/errors/NotFoundPage';
import { FeaturePlaceholder } from '../../features/placeholders/FeaturePlaceholder';
import { ExamListPage } from '../../features/exam-structure/pages/ExamListPage';
import { ExamEditorPage } from '../../features/exam-structure/pages/ExamEditorPage';
import { ExamVersionPage } from '../../features/exam-structure/pages/ExamVersionPage';
import { SourceListPage } from '../../features/sources/pages/SourceListPage';
import { SourceEditorPage } from '../../features/sources/pages/SourceEditorPage';
import { KnowledgeFactListPage } from '../../features/knowledge/pages/KnowledgeFactListPage';
import { KnowledgeFactEditorPage } from '../../features/knowledge/pages/KnowledgeFactEditorPage';
import { LearningObjectiveListPage } from '../../features/knowledge/pages/LearningObjectiveListPage';
import { LearningObjectiveEditorPage } from '../../features/knowledge/pages/LearningObjectiveEditorPage';
import { QuestionListPage } from '../../features/questions/pages/QuestionListPage';
import { QuestionEditorPage } from '../../features/questions/pages/QuestionEditorPage';

const placeholders = [
  ['reviews', 'Review Queue'], ['releases', 'Releases'], ['reports', 'Reports'], ['audit', 'Audit Log'],
] as const;

export function AppRouter() {
  return <Routes>
    <Route path="/login" element={<LoginPage />} />
    <Route path="/unauthorized" element={<UnauthorizedPage />} />
    <Route element={<ProtectedRoute />}>
      <Route element={<AdminLayout />}>
        <Route index element={<Navigate to="/dashboard" replace />} />
        <Route path="dashboard" element={<DashboardPage />} />
        <Route path="exam-structure" element={<ExamListPage />} />
        <Route path="exam-structure/exams/new" element={<ExamEditorPage />} />
        <Route path="exam-structure/exams/:id" element={<ExamEditorPage />} />
        <Route path="exam-structure/exam-versions/:id" element={<ExamVersionPage />} />
        <Route path="sources" element={<SourceListPage />} />
        <Route path="sources/new" element={<SourceEditorPage />} />
        <Route path="sources/:id" element={<SourceEditorPage />} />
        <Route path="knowledge" element={<KnowledgeFactListPage />} />
        <Route path="knowledge/facts/new" element={<KnowledgeFactEditorPage />} />
        <Route path="knowledge/facts/:id" element={<KnowledgeFactEditorPage />} />
        <Route path="knowledge/objectives" element={<LearningObjectiveListPage />} />
        <Route path="knowledge/objectives/new" element={<LearningObjectiveEditorPage />} />
        <Route path="knowledge/objectives/:id" element={<LearningObjectiveEditorPage />} />
        <Route path="questions" element={<QuestionListPage />} />
        <Route path="questions/new" element={<QuestionEditorPage />} />
        <Route path="questions/:id" element={<QuestionEditorPage />} />
        {placeholders.map(([path, name]) => <Route key={path} path={path} element={<FeaturePlaceholder name={name} />} />)}
      </Route>
    </Route>
    <Route path="*" element={<NotFoundPage />} />
  </Routes>;
}
