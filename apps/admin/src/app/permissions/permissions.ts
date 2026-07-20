export enum AdminRole { ContentAuthor = 'CONTENT_AUTHOR', ContentReviewer = 'CONTENT_REVIEWER', ContentPublisher = 'CONTENT_PUBLISHER', Admin = 'ADMIN' }
export enum Permission { ViewContent = 'VIEW_CONTENT', CreateDraftContent = 'CREATE_DRAFT_CONTENT', EditDraftContent = 'EDIT_DRAFT_CONTENT', SubmitContentForReview = 'SUBMIT_CONTENT_FOR_REVIEW', ReviewContent = 'REVIEW_CONTENT', ApproveContent = 'APPROVE_CONTENT', RejectContent = 'REJECT_CONTENT', CreateRelease = 'CREATE_RELEASE', PublishRelease = 'PUBLISH_RELEASE', ViewAuditLog = 'VIEW_AUDIT_LOG', ManageAdminAccess = 'MANAGE_ADMIN_ACCESS' }

const rolePermissions: Record<AdminRole, readonly Permission[]> = {
  [AdminRole.ContentAuthor]: [Permission.ViewContent, Permission.CreateDraftContent, Permission.EditDraftContent, Permission.SubmitContentForReview],
  [AdminRole.ContentReviewer]: [Permission.ViewContent, Permission.ReviewContent, Permission.ApproveContent, Permission.RejectContent],
  [AdminRole.ContentPublisher]: [Permission.ViewContent, Permission.CreateRelease, Permission.PublishRelease],
  [AdminRole.Admin]: Object.values(Permission),
};

export interface AdminIdentity { id: string; displayName: string; roles: AdminRole[] }
export function permissionsFor(admin: AdminIdentity): Permission[] { return [...new Set(admin.roles.flatMap((role) => rolePermissions[role] ?? []))]; }
export function can(admin: AdminIdentity | null, permission: Permission): boolean { return admin !== null && permissionsFor(admin).includes(permission); }
export function isAdminRole(value: string): value is AdminRole { return Object.values(AdminRole).includes(value as AdminRole); }
