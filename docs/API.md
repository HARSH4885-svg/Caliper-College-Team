# REST API

Base: `http://127.0.0.1:8080`. JSON unless noted. Auth: `Authorization: Bearer <token>` from login/register. Errors: `{"error": "message"}` with 400 validation, 401 unauthenticated, 403 wrong role, 404 not found **or not yours**, 409 conflict, 413 too large, 429 locked out. No response contains filesystem paths, password hashes or tokens other than the login response.

## Auth
| Method | Path | Who | Notes |
|---|---|---|---|
| POST | `/api/auth/login` | anyone | `{email,password}` → `{token,user}`; 5 failures / 5 min per email → 429 |
| POST | `/api/auth/register` | anyone | `{email,displayName,password(≥8),enrollmentCode}`; student only; email unique case-insensitively |
| POST | `/api/auth/logout` | user | 204; token is discarded client-side (stateless) |
| GET | `/api/auth/me` | user | |

## Courses
| GET | `/api/courses` | user | own courses (instructor) or enrolled (student); `enrollmentCode` only for the owner |
|---|---|---|---|
| POST | `/api/courses` | instructor | `{code,title}` |
| GET | `/api/courses/{id}` | member | |
| POST | `/api/courses/{id}/archive?archived=` | owner | hides from students; `GET /api/courses?includeArchived=true` for owner |
| PATCH | `/api/courses/{id}` | owner | `{code?,title?,rotateEnrollmentCode?}` |
| POST | `/api/courses/{id}/enroll` | student | `{enrollmentCode}` |
| GET | `/api/courses/{id}/students` | owner | |

## Assignments
| GET | `/api/courses/{id}/assignments` | member | students see published only |
|---|---|---|---|
| POST | `/api/courses/{id}/assignments` | owner | `{title,description,language(c|cpp|java|python|javascript),deadline,timeLimitMs,memoryLimitMb}` |
| GET | `/api/assignments/{id}` | member | students: sample tests + starter files + `hiddenTestCount` only |
| PATCH | `/api/assignments/{id}` | owner | |
| POST | `/api/assignments/{id}/test-cases` | owner | replaces the list: `[{name,input,expectedOutput,sample}]` |
| POST | `/api/assignments/{id}/files` | owner | multipart `kind=STARTER|REFERENCE`, `file` (≤1 MiB) |
| GET | `/api/assignments/{id}/files/{fileId}` | member | students: STARTER only (REFERENCE → 404) |
| POST | `/api/assignments/{id}/publish` | owner | 409 without test cases |
| POST | `/api/assignments/{id}/unpublish` | owner | back to draft |
| POST | `/api/assignments/{id}/archive?archived=` | owner | hidden from students; `?includeArchived=true` on lists |

## Submissions and evaluation
| POST | `/api/assignments/{id}/submissions` | student | multipart `file` (source or .zip); 409 after deadline or at 20 attempts |
|---|---|---|---|
| GET | `/api/assignments/{id}/submissions/me` | student | |
| GET | `/api/assignments/{id}/submissions?verdict=&status=` | owner | max 500 |
| GET | `/api/submissions/{id}` | owner of submission or course instructor | |
| GET | `/api/submissions/{id}/files/{fileId}` | same | `text/plain`, nosniff |
| GET | `/api/submissions/{id}/evaluation` | same | students get detail/excerpts only for sample tests; hidden tests appear as "Hidden test N" |
| POST | `/api/submissions/{id}/rejudge` | course instructor | 202 |

## Similarity & Plagiarism Dossiers (instructor of the course only)
| Method | Path | Who | Notes |
|---|---|---|---|
| POST | `/api/assignments/{id}/plagiarism/run` | owner | synchronous; needs ≥2 students with submissions |
| GET | `/api/assignments/{id}/plagiarism-pairs?state=` | owner | latest run, highest evidence first, includes `disclaimer` and per-submission insufficient-evidence notes |
| GET | `/api/plagiarism-pairs/{id}` | owner | metrics, explanation, fragments, AST structural fragments, file lists, review history |
| POST | `/api/plagiarism-pairs/{id}/review` | owner | `{decision: CONFIRMED_CONCERN|DISMISSED|INCONCLUSIVE, note?}` |
| GET | `/api/plagiarism-pairs/{id}/report.pdf` | owner | downloadable publication-grade PDF academic integrity dossier (OpenPDF) |
| GET | `/api/plagiarism-pairs/{id}/dossier?format=pdf\|html\|json` | owner | polymorphic dossier export: PDF, self-contained semantic HTML, or machine-readable JSON |
| GET | `/api/assignments/{id}/plagiarism-report.csv` | owner | one row per pair; formula-injection neutralised |

## 1EdTech LTI 1.3 Advantage Integration
| Method | Path | Who | Notes |
|---|---|---|---|
| GET | `/api/lti/jwks` | anyone | Public JSON Web Key Set containing Caliper's active RSA verification public keys |
| POST | `/api/lti/login-init` | LMS platform | OIDC 3rd-party launch initiation; validates issuer, redirects to LMS authorization endpoint |
| POST | `/api/lti/launch` | LMS student / instructor | Validates signed JWT `id_token`, auto-provisions or maps user, returns Caliper JWT |
| POST | `/api/lti/sync-grades/{assignmentId}` | instructor | Dispatches Assignment and Grade Service (AGS) line-item scores back to LMS |

## System Administration & Sandbox Inspector
| Method | Path | Who | Notes |
|---|---|---|---|
| GET | `/api/admin/sandbox/capabilities` | admin | Inspects active runtime (`docker` or `gvisor`), OS architecture, cgroups, and isolation features |

## Dashboards
`GET /api/dashboard/instructor`, `GET /api/dashboard/student` (role-specific, 403 otherwise).

Health: `GET /actuator/health` (no details). Everything else is denied.
| GET | `/api/assignments/{id}/analytics` | owner | latest attempt per student: verdict counts, score buckets, per-test pass rate, median runtime, pending count |
| GET | `/api/courses/{id}/gradebook` | owner | best attempt per student per published assignment: `{assignments, rows[{cells}]}` |
| GET | `/api/courses/{id}/gradebook.csv` | owner | same as CSV; formula-injection neutralised |
| POST | `/api/courses/join` | student | `{enrollmentCode}`; enrols by code alone |

## Account, announcements, search and bulk actions
| Method | Path | Who | Notes |
|---|---|---|---|
| PATCH | `/api/auth/me` | user | `{displayName}`; email and role are never editable |
| POST | `/api/auth/password` | user | `{currentPassword,newPassword(8-200)}` → 204; 400 if the current password is wrong or the new one is unchanged; 5 wrong tries / 10 min → 429; per-IP rate limited |
| GET | `/api/courses/{id}/announcements` | member | pinned first, then newest; max 100 |
| POST | `/api/courses/{id}/announcements` | owner | `{title(≤150),body(≤5000),pinned}` |
| POST | `/api/announcements/{id}/pin?pinned=` | owner | |
| DELETE | `/api/announcements/{id}` | owner | 204 |
| GET | `/api/courses/{id}/my-progress` | enrolled student | same shape as the gradebook but only the caller's row (403 for instructors) |
| GET | `/api/search?q=` | user | jump-to search, 2-80 chars, LIKE wildcards escaped; instructor: own courses, assignments and enrolled students; student: enrolled courses and published assignments only |
| POST | `/api/assignments/{id}/duplicate` | owner | copies statement, limits, tests and attachments into an unpublished draft due in 7 days |
| POST | `/api/assignments/{id}/rejudge?verdict=` | owner | verdict is AC, WA, CE, RE, TLE, MLE, IE or `all`; re-queues each student's latest finished attempt (max 500); returns `{queued}` |
| GET | `/api/assignments/{id}/submissions.csv` | owner | every attempt; formula-injection neutralised |
| GET | `/api/assignments/{id}/submissions?q=` | owner | `q` matches student name or email (in addition to `verdict`, `status`) |

## Courses (classroom expansion)
| Method | Path | Who | Notes |
|---|---|---|---|
| POST/PATCH | `/api/courses`, `/api/courses/{id}` | instructor / staff | also `description, subject, category, startsOn, endsOn` (end not before start) |
| GET | `/api/courses/{id}/staff` | member | lead, co-instructors, TAs (emails only for staff) |
| POST | `/api/courses/{id}/staff` | lead | `{email, role: CO_INSTRUCTOR|TA}`; instructor accounts only |
| DELETE | `/api/courses/{id}/staff/{userId}` | lead | |
| POST | `/api/courses/{id}/enrollments` | staff | `{emails[]}` → `{added, alreadyEnrolled, notFound}`; existing student accounts only |
| DELETE | `/api/courses/{id}/students/{studentId}` | staff | work stays on record |
| POST | `/api/courses/{id}/clone` | staff | details, assignments (drafts), tests, questions; no students or work |
| DELETE | `/api/courses/{id}` | lead | 409 once any student has handed in work |
| GET | `/api/courses/{id}/overview` | member | stats, upcoming, activity; staff also get "may need help"; students only their own data |
| GET | `/api/courses/{id}/analytics` | staff | enrolment by week, weekly activity, per-assignment stats, score spread, hardest questions, languages |
| GET | `/api/courses/{id}/gradebook`, `/my-progress` | staff / student | cells now carry `score, maxMarks, late, status` (GRADED, PENDING, IN_PROGRESS, RETURNED, MISSING, NOT_STARTED) and rows `totalScore, totalMax, percent, missing, late` |

Co-instructors and TAs pass every "course owner" check (`teaches()` in SQL) except staff changes and deleting the course.

## Assessments
Assignments have `kind` CODING or QUIZ and `options` on create/PATCH: `category, difficulty, maxMarks (coding), passingMarks, availableFrom/clearAvailableFrom, maxAttempts (1-50), latePolicy BLOCK|ALLOW|PENALTY, latePenaltyPct, lateUntil/clearLateUntil, quizMinutes/clearQuizMinutes, shuffleQuestions, shuffleOptions, releaseMode IMMEDIATE|AFTER_DUE|MANUAL, showAnswers`. Students do not see an assignment before `availableFrom`. Coding uploads honour the attempt limit and late policy (late work is flagged and the penalty applied to the score).

| Method | Path | Who | Notes |
|---|---|---|---|
| GET/PUT | `/api/assignments/{id}/questions` | staff | PUT saves the ordered list (rows with `id` updated, others inserted, missing deleted; 409 deleting an answered question); re-marks submitted attempts; quiz max marks = sum |
| GET | `/api/assignments/{id}/preview` | staff | the student view, no answer key |
| POST | `/api/assignments/{id}/questions/random` | staff | `{subject?, topic?, difficulty?, type?, count}` from the bank, unused ones only |
| GET | `/api/assignments/{id}/attempts` | staff | every attempt with `needsMarking` |
| POST | `/api/assignments/{id}/release`, `/regrade` | staff | release fully marked attempts / re-mark all |
| POST | `/api/assignments/{id}/attempts` | student | start or resume; enforces window, attempts, late policy |
| GET | `/api/assignments/{id}/attempts/me` | student | scores only when released |
| GET | `/api/attempts/{id}` | owner student / staff | key and explanations only after release (if `showAnswers`) or for staff |
| PUT | `/api/attempts/{id}/responses` | owner | `{answers: {questionId: response}}` autosave; refused after time runs out (30 s grace; the attempt is then submitted) |
| POST | `/api/attempts/{id}/submit` | owner | marks objective questions |
| POST/GET | `/api/attempts/{id}/files/{questionId}` | owner (POST); owner or staff (GET) | FILE questions, 5 MiB |
| POST | `/api/attempts/{id}/grade` | staff | `{marks:[{questionId, score, feedback}], feedback, privateNote, release?, returnForRevision}`; score within ±question marks |
| GET/POST | `/api/submissions/{id}/grade` | owner student (GET) / staff | coding override score, feedback, private note |
| GET/POST/PUT/DELETE | `/api/question-bank[/{id}]` | instructor (own bank only) | filters `q, type, difficulty, subject, topic` |

Question types: MCQ, MULTI (optional partial credit), TRUE_FALSE (MCQ/TF optional negative marking), SHORT (accepted answers, case-insensitive by default; empty key = hand-marked; used for SQL queries), LONG (hand-marked), FILL_BLANK (`___` in the text, per-blank partial credit), OUTPUT (code shown, exact output, trailing whitespace ignored), FILE (hand-marked).

## Running code (workspace)
| Method | Path | Who | Notes |
|---|---|---|---|
| POST | `/api/assignments/{id}/run` | member (student: published, visible) | `{source, mode: samples|custom|all, input?, test?}`; same sandbox and limits as grading; nothing is stored. Students: sample tests or custom input only (`all` → 403). Response per test: input/expected (samples and custom only), actual stdout, stderr, verdict, time. At most 2 interactive runs at once (20 s wait, then 429) and a per-user limit. Memory use is not reported. |
| POST | `/api/questions/{id}/run?attempt=` | the student's open attempt, or staff | code questions in quizzes; staff may use `mode=all` |

`CODE` questions (Flyway V5): `settings {language, starter, timeLimitMs}`, `answer {tests:[{name, input, expected, sample}]}`. On submit each code answer is queued and judged by `QuizCodeMarker` (marks = share of tests passed); the attempt shows "waiting for marks" until then. Students never receive hidden test inputs, expected outputs or program output, even after release; a judge failure leaves the answer for the instructor to mark.

## Materials, discussions, notifications, calendar (Flyway V6)
| Method | Path | Who | Notes |
|---|---|---|---|
| GET | `/api/courses/{id}/materials` | member | grouped by `module`, ordered |
| POST | `/api/courses/{id}/materials` | staff | `{module, title, kind: LINK|NOTE, url (http/https only), body}` |
| POST | `/api/courses/{id}/materials/file` | staff | multipart `file` (PDF, images, text/code, Office, ipynb, zip; ≤ 10 MiB; no HTML/SVG), `module`, `title?` |
| PATCH/DELETE | `/api/materials/{id}` | staff | `{title?, module?, body?, move: up|down}` |
| GET | `/api/materials/{id}/file` | member | attachment; real content type only for PDF/images/plain text, nosniff |
| GET/POST | `/api/courses/{id}/threads` | member | `?filter=all|unanswered|mine&q=`; "answered" = a staff reply or an endorsed reply |
| GET | `/api/threads/{id}` | member | hidden posts are blanked for students |
| POST | `/api/threads/{id}/posts` | member | 409 when locked (staff excepted) |
| POST | `/api/threads/{id}/moderate` | staff | `{pinned?, locked?, hidden?}` |
| POST | `/api/posts/{id}/endorse?on=`, `/api/posts/{id}/hide?on=` | staff (authors may hide their own) | |
| POST | `/api/threads/{id}/report` | member | `{postId?, reason}`; staff are notified |
| GET | `/api/courses/{id}/reports`, POST `/api/reports/{id}/resolve` | staff | |
| GET | `/api/notifications?unread=&limit=` | user | `{unread, items}`; for students also creates "due within 48 h" reminders |
| POST | `/api/notifications/{id}/read`, `/api/notifications/read-all` | user | |
| GET/PUT | `/api/notifications/preferences` | user | `{ASSIGNMENT, DEADLINE, ANNOUNCEMENT, GRADE, DISCUSSION, ENROLMENT: bool}` |
| GET | `/api/calendar?from=&to=` | user | ≤ 120 days; DUE / OPENS / LATE_CLOSES / COURSE_START / COURSE_END; `done` for students |

Notifications are sent when an assignment is published (once), an announcement is posted, quiz marks are released or an attempt is returned, coding feedback or an override is saved, a thread gets a reply, a student asks a question (to staff), or a post is reported. In-app only: there is no mail service.
