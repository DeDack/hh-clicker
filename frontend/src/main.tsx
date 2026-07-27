import React, { FormEvent, useEffect, useMemo, useState } from 'react';
import ReactDOM from 'react-dom/client';
import { QueryClient, QueryClientProvider, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { BrowserRouter, Link, Navigate, Route, Routes, useLocation, useNavigate, useParams } from 'react-router-dom';
import './styles.css';

const queryClient = new QueryClient();
const API = import.meta.env.VITE_BACKEND_URL || '';

type User = { id: string; email: string; role: string; status: string; features: { coverLetterGenerationEnabled: boolean } };
type HhAccount = { id: string; name: string; status: string; lastCheckedAt?: string };
type Resume = { id: string; hhAccountId: string; hhAccountName?: string; title: string; text: string; candidateProfile?: string; telegramUsername?: string; active: boolean; lastSyncedAt?: string };
type SavedSearch = {
  id: string; name: string; hhAccountId: string; hhAccountName?: string; resumeId: string; resumeName?: string; searchUrl: string; pages: number;
  vacancyLoadLimit?: number;
  includeKeywords?: string; excludeKeywords?: string; defaultCoverLetterMode?: string; defaultCommonCoverLetter?: string;
  defaultDelaySeconds?: number; defaultMaxApplications?: number; active: boolean; updatedAt?: string;
};
type Campaign = {
  id: string; name: string; status: string; hhAccountId: string; hhAccountName?: string; resumeId: string; resumeName?: string; savedSearchId?: string; savedSearchName?: string;
  searchUrl: string; pages: number; vacancyLoadLimit?: number; includeKeywords?: string; excludeKeywords?: string; coverLetterMode?: string; commonCoverLetter?: string;
  reviewCoverLettersBeforeApply: boolean; delaySeconds?: number; maxApplications?: number; stopRequested: boolean;
  totalVacancies: number; generatedCount: number; appliedCount: number; alreadyCount: number; skippedCount: number; failedCount: number; createdAt?: string;
};
type Vacancy = {
  id: string; title: string; companyName?: string; vacancyUrl: string; description?: string; sourcePage: number; selected: boolean; alreadyApplied: boolean;
  coverLetter?: string; coverLetterStatus: string; coverLetterEditedManually: boolean; generationError?: string; applyStatus: string; applyErrorCode?: string;
};

let accessToken = localStorage.getItem('accessToken') || '';

async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set('Content-Type', 'application/json');
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`);
  const res = await fetch(`${API}${path}`, { ...init, headers, credentials: 'include' });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ message: res.statusText }));
    throw new Error(err.message || err.code || 'Ошибка запроса');
  }
  if (res.status === 204) return undefined as T;
  return res.json();
}

function useMe() {
  return useQuery({ queryKey: ['me'], queryFn: () => api<User>('/api/auth/me'), retry: false });
}

const statusLabel: Record<string, string> = {
  DRAFT: 'Черновик',
  PREVIEW_LOADING: 'Загружаем вакансии',
  PREVIEW_READY: 'Вакансии загружены',
  LETTERS_GENERATING: 'Генерируем письма',
  READY_TO_APPLY: 'Готова к отправке',
  APPLYING: 'Отправляем отклики',
  STOPPING: 'Останавливаем',
  STOPPED: 'Остановлена',
  COMPLETED: 'Завершена',
  FAILED: 'Ошибка',
  INTERRUPTED: 'Прервана после перезапуска',
};
const modeLabel: Record<string, string> = { NONE: 'Без письма', COMMON: 'Одно общее письмо', PERSONAL_AI: 'Персональное письмо через ИИ', personal: 'Персональное письмо через ИИ', common: 'Одно общее письмо' };
const letterStatusLabel: Record<string, string> = {
  PENDING: 'Письмо не готово', GENERATING: 'Письмо генерируется', GENERATED: 'Письмо сгенерировано', EDITED: 'Письмо изменено',
  FAILED: 'Ошибка генерации', SKIPPED: 'Исключена', PROFILE_MISMATCH: 'Резюме плохо подходит',
};
const applyStatusLabel: Record<string, string> = {
  PENDING: 'Не отправляли', SENDING: 'Отправляем', SENT: 'Отправлено', ALREADY_APPLIED: 'Отклик уже был отправлен ранее',
  TEST_REQUIRED: 'Нужен тест', LIMIT_EXCEEDED: 'Лимит HH', AUTH_ERROR: 'Нужна новая сессия', SKIPPED: 'Исключена',
  LETTER_GENERATION_FAILED: 'Письмо не сгенерировалось', FAILED: 'Ошибка отправки',
};

function normalizeMode(mode?: string) {
  if (!mode) return 'PERSONAL_AI';
  if (mode.toLowerCase() === 'personal') return 'PERSONAL_AI';
  if (mode.toLowerCase() === 'common') return 'COMMON';
  return mode.toUpperCase();
}

function normalizeOptionalNumber(value: unknown) {
  if (value === null || value === undefined || value === '') return null;
  const number = Number(value);
  return Number.isFinite(number) && number > 0 ? number : null;
}

function formatDate(raw?: string) {
  if (!raw) return 'нет данных';
  return new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit' }).format(new Date(raw));
}

function safeError(code?: string) {
  if (!code) return '';
  if (code === 'ADAPTER_UNAVAILABLE') return 'Не удалось связаться с внутренним сервисом HH. Повторите попытку.';
  if (code === 'COVER_LETTERS_NOT_READY') return 'Для выбранной вакансии ещё нет готового письма.';
  if (code === 'LETTER_GENERATION_FAILED') return 'Не удалось сгенерировать письмо для этой вакансии.';
  return 'Операция завершилась ошибкой.';
}

const ToastContext = React.createContext<(message: string) => void>(() => undefined);
function ToastProvider({ children }: { children: React.ReactNode }) {
  const [items, setItems] = useState<{ id: number; message: string }[]>([]);
  function push(message: string) {
    const id = Date.now();
    setItems(x => [...x, { id, message }]);
    window.setTimeout(() => setItems(x => x.filter(t => t.id !== id)), 2600);
  }
  return <ToastContext.Provider value={push}>{children}<div className="toasts">{items.map(t => <div className="toast" key={t.id}>{t.message}</div>)}</div></ToastContext.Provider>;
}
function useToast() { return React.useContext(ToastContext); }

const nav = [
  ['/dashboard', 'Dashboard'],
  ['/hh-accounts', 'HH-аккаунты'],
  ['/resumes', 'Резюме'],
  ['/searches', 'Поиски'],
  ['/campaigns', 'Кампании'],
] as const;

function Shell({ children }: { children: React.ReactNode }) {
  const me = useMe();
  const navigate = useNavigate();
  const location = useLocation();
  async function logout() {
    await api('/api/auth/logout', { method: 'POST' }).catch(() => undefined);
    accessToken = '';
    localStorage.removeItem('accessToken');
    queryClient.clear();
    navigate('/login');
  }
  return <div className="app-shell">
    <aside>
      <h1>HH Clicker</h1>
      <nav>
        {nav.map(([to, label]) => <Link className={location.pathname.startsWith(to) ? 'active' : ''} key={to} to={to}>{label}</Link>)}
        {me.data?.role === 'ADMIN' && <Link className={location.pathname.startsWith('/admin') ? 'active' : ''} to="/admin/users">Admin</Link>}
      </nav>
      <div className="side-note">
        <strong>{me.data?.email}</strong>
        <span>ИИ для пользователя: {me.data?.features.coverLetterGenerationEnabled ? 'доступен' : 'недоступен'}</span>
        <button className="ghost" onClick={logout}>Выйти</button>
      </div>
    </aside>
    <main>{children}</main>
  </div>;
}

function Protected({ children }: { children: React.ReactNode }) {
  const me = useMe();
  if (me.isLoading) return <div className="auth-page">Загрузка...</div>;
  if (me.isError) return <Navigate to="/login" replace />;
  return <Shell>{children}</Shell>;
}

function AuthPage({ mode }: { mode: 'login' | 'register' }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const navigate = useNavigate();
  async function submit(e: FormEvent) {
    e.preventDefault();
    setError('');
    try {
      const res = await api<{ accessToken: string }>(`/api/auth/${mode}`, { method: 'POST', body: JSON.stringify({ email, password }) });
      accessToken = res.accessToken;
      localStorage.setItem('accessToken', accessToken);
      await queryClient.invalidateQueries({ queryKey: ['me'] });
      navigate('/dashboard');
    } catch (err) {
      setError((err as Error).message);
    }
  }
  return <div className="auth-page"><form className="panel narrow" onSubmit={submit}>
    <h2>{mode === 'login' ? 'Вход' : 'Регистрация'}</h2>
    {error && <p className="error">{error}</p>}
    <input value={email} onChange={e => setEmail(e.target.value)} placeholder="email@example.com" />
    <input value={password} onChange={e => setPassword(e.target.value)} placeholder="Пароль" type="password" />
    <button>{mode === 'login' ? 'Войти' : 'Создать аккаунт'}</button>
    <Link className="muted-link" to={mode === 'login' ? '/register' : '/login'}>{mode === 'login' ? 'Регистрация' : 'Уже есть аккаунт'}</Link>
  </form></div>;
}

function Page({ title, action, children }: { title: string; action?: React.ReactNode; children: React.ReactNode }) {
  return <section className="page"><header className="page-head"><h2>{title}</h2>{action}</header>{children}</section>;
}
function Card({ label, value, hint }: { label: string; value: React.ReactNode; hint?: React.ReactNode }) {
  return <div className="card"><span>{label}</span><strong>{value}</strong>{hint && <small>{hint}</small>}</div>;
}
function Field({ label, children, hint }: { label: string; children: React.ReactNode; hint?: string }) {
  return <label className="field"><span>{label}</span>{children}{hint && <small>{hint}</small>}</label>;
}
function Select({ data, label, value, onChange }: { data?: any[]; label: string; value: string; onChange: (id: string) => void }) {
  return <select value={value} onChange={e => onChange(e.target.value)}><option value="" disabled>{label}</option>{data?.map(x => <option key={x.id} value={x.id}>{x.name || x.title}</option>)}</select>;
}
function Empty({ text }: { text: string }) { return <div className="empty">{text}</div>; }

function Dashboard() {
  const me = useMe();
  const llm = useQuery({ queryKey: ['llm'], queryFn: () => api<any>('/api/system/llm/status'), retry: false });
  const accounts = useQuery({ queryKey: ['accounts'], queryFn: () => api<HhAccount[]>('/api/hh-accounts') });
  const resumes = useQuery({ queryKey: ['resumes'], queryFn: () => api<Resume[]>('/api/resumes') });
  const campaigns = useQuery({ queryKey: ['campaigns'], queryFn: () => api<Campaign[]>('/api/campaigns') });
  const active = campaigns.data?.filter(c => ['PREVIEW_LOADING', 'LETTERS_GENERATING', 'APPLYING', 'STOPPING'].includes(c.status)).length ?? 0;
  const sent = campaigns.data?.reduce((sum, c) => sum + c.appliedCount, 0) ?? 0;
  const errors = campaigns.data?.reduce((sum, c) => sum + c.failedCount, 0) ?? 0;
  return <Page title="Dashboard">
    <div className="stats">
      <Card label="HH-аккаунты" value={`${accounts.data?.filter(a => a.status === 'ACTIVE').length ?? 0} активен`} hint={`${accounts.data?.filter(a => a.status !== 'ACTIVE').length ?? 0} требуют обновления`} />
      <Card label="Резюме" value={resumes.data?.length ?? 0} />
      <Card label="Активные кампании" value={active} />
      <Card label="Отправлено" value={sent} />
      <Card label="Ошибок" value={errors} />
      <Card label="ИИ" value={me.data?.features.coverLetterGenerationEnabled ? 'доступен' : 'недоступен'} hint={llm.data?.reachable ? 'LLM-сервис отвечает' : 'LLM-сервис не проверен'} />
    </div>
    <h3 className="section-title">Последние кампании</h3>
    <div className="list">{campaigns.data?.slice(0, 5).map(c => <CampaignListCard key={c.id} campaign={c} />) || <Empty text="Кампаний пока нет." />}</div>
  </Page>;
}

function HhAccountsPage() {
  const qc = useQueryClient();
  const toast = useToast();
  const [showConnect, setShowConnect] = useState(false);
  const [refreshAccount, setRefreshAccount] = useState<HhAccount | null>(null);
  const [name, setName] = useState('');
  const [rawCurl, setRawCurl] = useState('');
  const [refreshCurl, setRefreshCurl] = useState('');
  const accounts = useQuery({ queryKey: ['accounts'], queryFn: () => api<HhAccount[]>('/api/hh-accounts') });
  const resumes = useQuery({ queryKey: ['resumes'], queryFn: () => api<Resume[]>('/api/resumes') });
  const refreshAccountData = () => {
    qc.invalidateQueries({ queryKey: ['accounts'] });
    qc.invalidateQueries({ queryKey: ['resumes'] });
    qc.invalidateQueries({ queryKey: ['searches'] });
    qc.invalidateQueries({ queryKey: ['campaigns'] });
  };
  const create = useMutation({
    mutationFn: () => api('/api/hh-accounts', { method: 'POST', body: JSON.stringify({ name, rawCurl }) }),
    onSuccess: () => { setRawCurl(''); setName(''); setShowConnect(false); toast('HH-аккаунт подключён, резюме синхронизированы'); refreshAccountData(); }
  });
  const refresh = useMutation({
    mutationFn: () => api(`/api/hh-accounts/${refreshAccount?.id}/refresh-session`, { method: 'POST', body: JSON.stringify({ rawCurl: refreshCurl }) }),
    onSuccess: () => { setRefreshCurl(''); setRefreshAccount(null); toast('Сессия обновлена, резюме синхронизированы'); refreshAccountData(); }
  });
  const run = (promise: Promise<unknown>, message: string) => promise.then(() => { toast(message); refreshAccountData(); }).catch(e => toast((e as Error).message));
  return <Page title="HH-аккаунты" action={<button onClick={() => { setRefreshAccount(null); setShowConnect(true); }}>Подключить HH-аккаунт</button>}>
    <div className="hero-strip">
      <div><strong>{accounts.data?.filter(a => a.status === 'ACTIVE').length ?? 0}</strong><span>активных аккаунтов</span></div>
      <div><strong>{resumes.data?.length ?? 0}</strong><span>резюме загружено</span></div>
      <div><strong>{accounts.data?.filter(a => a.status !== 'ACTIVE').length ?? 0}</strong><span>требуют внимания</span></div>
    </div>
    {showConnect && <form className="drawer" onSubmit={e => { e.preventDefault(); create.mutate(); }}>
      <h3>Подключить новый HH-аккаунт</h3>
      <Field label="Название"><input value={name} onChange={e => setName(e.target.value)} placeholder="Али джава" /></Field>
      <Field label="cURL из HH" hint="Если аккаунт уже есть, используйте кнопку «Обновить сессию» на его карточке."><textarea value={rawCurl} onChange={e => setRawCurl(e.target.value)} placeholder="Copy as cURL из браузера" /></Field>
      <div className="toolbar"><button disabled={create.isPending}>Подключить</button><button type="button" className="ghost" onClick={() => setShowConnect(false)}>Отмена</button></div>
      {create.error && <p className="error">{create.error.message}</p>}
    </form>}
    {refreshAccount && <form className="drawer" onSubmit={e => { e.preventDefault(); refresh.mutate(); }}>
      <h3>Обновить сессию: {refreshAccount.name}</h3>
      <Field label="Новый cURL из HH"><textarea value={refreshCurl} onChange={e => setRefreshCurl(e.target.value)} placeholder="Copy as cURL из браузера" /></Field>
      <div className="toolbar"><button disabled={refresh.isPending}>Обновить сессию</button><button type="button" className="ghost" onClick={() => setRefreshAccount(null)}>Отмена</button></div>
      {refresh.error && <p className="error">{refresh.error.message}</p>}
    </form>}
    <div className="cards-grid">{accounts.data?.map(a => <div className="item account-card" key={a.id}>
      <div className="card-title-row"><h3>{a.name}</h3><span className={a.status === 'ACTIVE' ? 'status-ok' : 'status-bad'}>{a.status === 'ACTIVE' ? 'Активен' : 'Требует обновления'}</span></div>
      <p>Последняя проверка: {formatDate(a.lastCheckedAt)}</p>
      <p>Резюме: {resumes.data?.filter(r => r.hhAccountId === a.id).length ?? 0}</p>
      <div className="toolbar">
        <button onClick={() => run(api(`/api/hh-accounts/${a.id}/check`, { method: 'POST' }), 'Проверка выполнена')}>Проверить</button>
        <button className="ghost" onClick={() => { setShowConnect(false); setRefreshAccount(a); }}>Обновить сессию</button>
        <button className="ghost" onClick={() => run(api(`/api/hh-accounts/${a.id}/resumes/sync`, { method: 'POST' }), 'Резюме синхронизированы')}>Синхронизировать резюме</button>
        <button className="danger" onClick={() => confirm(`Удалить HH-аккаунт ${a.name}? Связанные резюме, поиски и кампании тоже будут удалены.`) && run(api(`/api/hh-accounts/${a.id}`, { method: 'DELETE' }), 'HH-аккаунт удалён')}>Удалить</button>
      </div>
    </div>)}</div>
    {!accounts.data?.length && <Empty text="Подключите HH-аккаунт, чтобы загрузить резюме и создать поиски." />}
  </Page>;
}

function ResumesPage() {
  const qc = useQueryClient();
  const toast = useToast();
  const accounts = useQuery({ queryKey: ['accounts'], queryFn: () => api<HhAccount[]>('/api/hh-accounts') });
  const resumes = useQuery({ queryKey: ['resumes'], queryFn: () => api<Resume[]>('/api/resumes') });
  const [profile, setProfile] = useState<Record<string, string>>({});
  const [telegram, setTelegram] = useState<Record<string, string>>({});
  return <Page title="Резюме">
    <div className="toolbar">{accounts.data?.map(a => <button key={a.id} onClick={() => api(`/api/hh-accounts/${a.id}/resumes/sync`, { method: 'POST' }).then(() => { toast('Резюме обновлены с HH'); qc.invalidateQueries({ queryKey: ['resumes'] }); qc.invalidateQueries({ queryKey: ['accounts'] }); }).catch(e => toast((e as Error).message))}>Обновить с HH: {a.name}</button>)}</div>
    <div className="list">{resumes.data?.map(r => <div className="item resume-card" key={r.id}>
      <div className="card-title-row"><h3>{r.title}</h3><span className={r.active ? 'status-ok' : 'status-bad'}>{r.active ? 'Активно' : 'Отключено'}</span></div>
      <p>HH-аккаунт: {r.hhAccountName || accounts.data?.find(a => a.id === r.hhAccountId)?.name || 'неизвестно'}</p>
      <p>Обновлено: {formatDate(r.lastSyncedAt)}</p>
      <Field label="Telegram username" hint="Без @. ИИ добавит контакт в конец персонального письма."><input value={telegram[r.id] ?? r.telegramUsername ?? ''} onChange={e => setTelegram({ ...telegram, [r.id]: e.target.value })} placeholder="username" /></Field>
      <Field label="Расширенный профиль"><textarea value={profile[r.id] ?? r.candidateProfile ?? ''} onChange={e => setProfile({ ...profile, [r.id]: e.target.value })} /></Field>
      <div className="toolbar"><button onClick={() => api(`/api/resumes/${r.id}/profile`, { method: 'PUT', body: JSON.stringify({ candidateProfile: profile[r.id] ?? r.candidateProfile ?? '', telegramUsername: telegram[r.id] ?? r.telegramUsername ?? '' }) }).then(() => { toast('Профиль сохранён'); qc.invalidateQueries({ queryKey: ['resumes'] }); }).catch(e => toast((e as Error).message))}>Сохранить профиль</button></div>
    </div>)}</div>
    {!resumes.data?.length && <Empty text="Резюме появятся после подключения аккаунта или синхронизации с HH. Если аккаунт уже есть, нажмите «Синхронизировать резюме» на его карточке." />}
  </Page>;
}

function SearchesPage() {
  const qc = useQueryClient();
  const toast = useToast();
  const searches = useQuery({ queryKey: ['searches'], queryFn: () => api<SavedSearch[]>('/api/saved-searches') });
  const navigate = useNavigate();
  return <Page title="Сохранённые поиски" action={<Link className="button-link" to="/searches/new">+ Новый поиск</Link>}>
    <div className="list">{searches.data?.map(s => <div className="item search-card" key={s.id}>
      <div>
        <h3>{s.name}</h3>
        <p>Резюме: {s.resumeName || s.resumeId}</p>
        <p>HH-аккаунт: {s.hhAccountName || s.hhAccountId}</p>
        <p>{s.pages} стр. · загрузить {s.vacancyLoadLimit || 'сколько сможет'} · откликов {s.defaultMaxApplications || 'без лимита'} · {modeLabel[normalizeMode(s.defaultCoverLetterMode)]}</p>
        <p>Изменён: {formatDate(s.updatedAt)}</p>
        <a className="text-link" href={s.searchUrl} target="_blank" title={s.searchUrl}>Открыть поиск на HH</a>
      </div>
      <div className="toolbar">
        <button onClick={() => navigate(`/campaigns/new?savedSearchId=${s.id}`)}>Создать кампанию</button>
        <Link className="button-link ghost-link" to={`/searches/${s.id}/edit`}>Редактировать</Link>
        <button className="danger" onClick={() => confirm(`Удалить поиск ${s.name}?`) && api(`/api/saved-searches/${s.id}`, { method: 'DELETE' }).then(() => { toast('Поиск удалён'); qc.invalidateQueries({ queryKey: ['searches'] }); })}>Удалить</button>
      </div>
    </div>)}</div>
    {!searches.data?.length && <Empty text="Сохраните первый поиск, чтобы потом создавать кампании без копирования настроек." />}
  </Page>;
}

function SearchFormPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const toast = useToast();
  const accounts = useQuery({ queryKey: ['accounts'], queryFn: () => api<HhAccount[]>('/api/hh-accounts') });
  const resumes = useQuery({ queryKey: ['resumes'], queryFn: () => api<Resume[]>('/api/resumes') });
  const searches = useQuery({ queryKey: ['searches'], queryFn: () => api<SavedSearch[]>('/api/saved-searches') });
  const editing = searches.data?.find(s => s.id === id);
  const [form, setForm] = useState<any>({ pages: 50, defaultDelaySeconds: 1, defaultMaxApplications: 0, defaultCoverLetterMode: 'PERSONAL_AI', active: true });
  useEffect(() => { if (editing) setForm({ ...editing, defaultCoverLetterMode: normalizeMode(editing.defaultCoverLetterMode) }); }, [editing?.id]);
  useEffect(() => { if (!form.hhAccountId && accounts.data?.length) setForm((f: any) => ({ ...f, hhAccountId: accounts.data![0].id })); }, [accounts.data, form.hhAccountId]);
  const accountResumes = resumes.data?.filter(r => !form.hhAccountId || r.hhAccountId === form.hhAccountId) ?? [];
  useEffect(() => { if ((!form.resumeId || !accountResumes.some(r => r.id === form.resumeId)) && accountResumes.length) setForm((f: any) => ({ ...f, resumeId: accountResumes[0].id })); }, [accountResumes, form.resumeId]);
  const canSubmit = !!form.name?.trim() && !!form.searchUrl?.trim() && !!form.hhAccountId && !!form.resumeId && accountResumes.length > 0;
  const save = useMutation({
    mutationFn: () => api(`/api/saved-searches${id ? `/${id}` : ''}`, { method: id ? 'PUT' : 'POST', body: JSON.stringify({ ...form, vacancyLoadLimit: normalizeOptionalNumber(form.vacancyLoadLimit) }) }),
    onSuccess: () => { toast(id ? 'Поиск обновлён' : 'Поиск сохранён'); qc.invalidateQueries({ queryKey: ['searches'] }); navigate('/searches'); }
  });
  return <Page title={id ? 'Редактировать поиск' : 'Новый поиск'}>
    <form className="form-grid" onSubmit={e => { e.preventDefault(); save.mutate(); }}>
      <Field label="Название"><input value={form.name || ''} placeholder="Java backend Казань" onChange={e => setForm({ ...form, name: e.target.value })} /></Field>
      <Field label="URL поиска HH"><input value={form.searchUrl || ''} placeholder="https://hh.ru/search/vacancy?..." onChange={e => setForm({ ...form, searchUrl: e.target.value })} /></Field>
      <Field label="HH-аккаунт"><Select value={form.hhAccountId || ''} data={accounts.data} label="Выберите аккаунт" onChange={v => setForm({ ...form, hhAccountId: v, resumeId: '' })} /></Field>
      <Field label="Резюме"><Select value={form.resumeId || ''} data={accountResumes} label="Выберите резюме" onChange={v => setForm({ ...form, resumeId: v })} /></Field>
      <Field label="Страниц"><input type="number" min={1} max={50} value={form.pages} onChange={e => setForm({ ...form, pages: Number(e.target.value) })} /></Field>
      <Field label="Сколько вакансий загрузить" hint="Оставь пустым, чтобы загрузить сколько получится по выбранным страницам."><input type="number" min={0} value={form.vacancyLoadLimit ?? ''} placeholder="без лимита" onChange={e => setForm({ ...form, vacancyLoadLimit: e.target.value })} /></Field>
      <Field label="Максимум откликов"><input type="number" min={0} value={form.defaultMaxApplications} onChange={e => setForm({ ...form, defaultMaxApplications: Number(e.target.value) })} /></Field>
      <Field label="Include keywords"><input value={form.includeKeywords || ''} onChange={e => setForm({ ...form, includeKeywords: e.target.value })} /></Field>
      <Field label="Exclude keywords"><input value={form.excludeKeywords || ''} onChange={e => setForm({ ...form, excludeKeywords: e.target.value })} /></Field>
      <Field label="Режим письма"><select value={normalizeMode(form.defaultCoverLetterMode)} onChange={e => setForm({ ...form, defaultCoverLetterMode: e.target.value })}><option value="NONE">Без письма</option><option value="COMMON">Одно общее письмо</option><option value="PERSONAL_AI">Персональные ИИ-письма</option></select></Field>
      <Field label="Задержка, сек"><input type="number" min={0} step="0.5" value={form.defaultDelaySeconds} onChange={e => setForm({ ...form, defaultDelaySeconds: Number(e.target.value) })} /></Field>
      {normalizeMode(form.defaultCoverLetterMode) === 'COMMON' && <Field label="Общее письмо"><textarea value={form.defaultCommonCoverLetter || ''} onChange={e => setForm({ ...form, defaultCommonCoverLetter: e.target.value })} /></Field>}
      {!accounts.data?.length && <p className="warning form-span">Сначала подключите HH-аккаунт.</p>}
      {!!accounts.data?.length && !accountResumes.length && <p className="warning form-span">Для выбранного HH-аккаунта нет резюме. Откройте HH-аккаунты и нажмите «Синхронизировать резюме».</p>}
      <div className="toolbar form-span"><button disabled={!canSubmit || save.isPending}>Сохранить</button><Link className="button-link ghost-link" to="/searches">Отмена</Link>{save.error && <p className="error">{save.error.message}</p>}</div>
    </form>
  </Page>;
}

function CampaignsPage() {
  const qc = useQueryClient();
  const toast = useToast();
  const campaigns = useQuery({ queryKey: ['campaigns'], queryFn: () => api<Campaign[]>('/api/campaigns'), refetchInterval: q => (q.state.data as Campaign[] | undefined)?.some(c => ['PREVIEW_LOADING', 'LETTERS_GENERATING', 'APPLYING', 'STOPPING'].includes(c.status)) ? 3000 : false });
  const remove = useMutation({
    mutationFn: (campaignId: string) => api<void>(`/api/campaigns/${campaignId}`, { method: 'DELETE' }),
    onSuccess: () => { toast('Кампания удалена'); qc.invalidateQueries({ queryKey: ['campaigns'] }); },
    onError: e => toast((e as Error).message),
  });
  return <Page title="Кампании" action={<Link className="button-link" to="/campaigns/new">Новая кампания</Link>}>
    <div className="list">{campaigns.data?.map(c => <CampaignListCard key={c.id} campaign={c} deleting={remove.isPending} onDelete={() => {
      if (confirm(`Удалить кампанию «${c.name}»?\n\nВакансии, письма и попытки отправки внутри этой кампании будут удалены.`)) remove.mutate(c.id);
    }} />)}</div>
    {!campaigns.data?.length && <Empty text="Создайте кампанию из сохранённого поиска или нового URL." />}
  </Page>;
}

function CampaignListCard({ campaign: c, deleting = false, onDelete }: { campaign: Campaign; deleting?: boolean; onDelete?: () => void }) {
  const running = ['PREVIEW_LOADING', 'LETTERS_GENERATING', 'APPLYING', 'STOPPING'].includes(c.status);
  return <div className="item campaign-card">
    <Link className="campaign-card-body" to={`/campaigns/${c.id}`}>
      <h3>{c.name}</h3>
      <p>Резюме: {c.resumeName || c.resumeId}</p>
      <p>HH: {c.hhAccountName || c.hhAccountId}</p>
      {c.savedSearchName && <p>Поиск: {c.savedSearchName}</p>}
      <p>{c.totalVacancies} вакансий · {c.appliedCount} отправлено · ошибок {c.failedCount}</p>
      <span className="badge">{statusLabel[c.status] || c.status}</span>
      <small>Создана: {formatDate(c.createdAt)}</small>
    </Link>
    <div className="toolbar card-actions">
      <Link className="button-link ghost-link" to={`/campaigns/${c.id}`}>Открыть</Link>
      {onDelete && <button className="danger" disabled={running || deleting} onClick={onDelete}>Удалить</button>}
    </div>
  </div>;
}

function NewCampaignPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const toast = useToast();
  const accounts = useQuery({ queryKey: ['accounts'], queryFn: () => api<HhAccount[]>('/api/hh-accounts') });
  const resumes = useQuery({ queryKey: ['resumes'], queryFn: () => api<Resume[]>('/api/resumes') });
  const searches = useQuery({ queryKey: ['searches'], queryFn: () => api<SavedSearch[]>('/api/saved-searches') });
  const initialSaved = new URLSearchParams(location.search).get('savedSearchId') || '';
  const [source, setSource] = useState<'saved' | 'new'>('saved');
  const [savedSearchId, setSavedSearchId] = useState(initialSaved);
  const [form, setForm] = useState<any>({ name: '', pages: 50, delaySeconds: 1, maxApplications: 0, coverLetterMode: 'PERSONAL_AI', reviewCoverLettersBeforeApply: true });
  const selectedSearch = searches.data?.find(s => s.id === savedSearchId);
  useEffect(() => { if (!savedSearchId && searches.data?.length) setSavedSearchId(searches.data[0].id); }, [searches.data, savedSearchId]);
  useEffect(() => {
    if (!selectedSearch || source !== 'saved') return;
    setForm((f: any) => ({
      ...f,
      name: f.name || selectedSearch.name,
      hhAccountId: selectedSearch.hhAccountId,
      resumeId: selectedSearch.resumeId,
      searchUrl: selectedSearch.searchUrl,
      pages: selectedSearch.pages,
      vacancyLoadLimit: selectedSearch.vacancyLoadLimit ?? '',
      includeKeywords: selectedSearch.includeKeywords || '',
      excludeKeywords: selectedSearch.excludeKeywords || '',
      delaySeconds: selectedSearch.defaultDelaySeconds ?? 1,
      maxApplications: selectedSearch.defaultMaxApplications ?? 0,
      coverLetterMode: normalizeMode(selectedSearch.defaultCoverLetterMode),
      commonCoverLetter: selectedSearch.defaultCommonCoverLetter || '',
    }));
  }, [selectedSearch?.id, source]);
  useEffect(() => { if (source === 'new' && !form.hhAccountId && accounts.data?.length) setForm((f: any) => ({ ...f, hhAccountId: accounts.data![0].id })); }, [source, accounts.data, form.hhAccountId]);
  const accountResumes = resumes.data?.filter(r => !form.hhAccountId || r.hhAccountId === form.hhAccountId) ?? [];
  useEffect(() => { if (source === 'new' && (!form.resumeId || !accountResumes.some(r => r.id === form.resumeId)) && accountResumes.length) setForm((f: any) => ({ ...f, resumeId: accountResumes[0].id })); }, [source, accountResumes, form.resumeId]);
  const create = useMutation({
    mutationFn: () => {
      const body = source === 'saved'
        ? { savedSearchId, name: form.name || selectedSearch?.name, overrides: { pages: form.pages, vacancyLoadLimit: normalizeOptionalNumber(form.vacancyLoadLimit), maxApplications: form.maxApplications, delaySeconds: form.delaySeconds, coverLetterMode: form.coverLetterMode, commonCoverLetter: form.commonCoverLetter, reviewCoverLettersBeforeApply: form.reviewCoverLettersBeforeApply, includeKeywords: form.includeKeywords, excludeKeywords: form.excludeKeywords } }
        : { ...form, vacancyLoadLimit: normalizeOptionalNumber(form.vacancyLoadLimit) };
      return api<Campaign>('/api/campaigns', { method: 'POST', body: JSON.stringify(body) });
    },
    onSuccess: async c => {
      toast('Кампания создана, загружаем вакансии');
      await api(`/api/campaigns/${c.id}/preview`, { method: 'POST' }).catch(e => toast((e as Error).message));
      navigate(`/campaigns/${c.id}`);
    }
  });
  const canSubmit = !!form.name?.trim() && (source === 'saved' ? !!savedSearchId : !!form.searchUrl?.trim() && !!form.hhAccountId && !!form.resumeId);
  return <Page title="Новая кампания">
    <form className="steps" onSubmit={e => { e.preventDefault(); if (!create.isPending) create.mutate(); }}>
      <div className="step"><h3>1. Источник</h3><div className="segmented"><button type="button" className={source === 'saved' ? 'selected' : 'ghost'} onClick={() => setSource('saved')}>Сохранённый поиск</button><button type="button" className={source === 'new' ? 'selected' : 'ghost'} onClick={() => setSource('new')}>Новый URL поиска</button></div>
        {source === 'saved' && <Field label="Сохранённый поиск"><select value={savedSearchId} onChange={e => setSavedSearchId(e.target.value)}>{searches.data?.map(s => <option key={s.id} value={s.id}>{s.name} — {s.hhAccountName} — {s.resumeName}</option>)}</select></Field>}
      </div>
      <div className="step"><h3>2. Резюме и HH-аккаунт</h3><div className="form-grid">
        <Field label="Название кампании"><input value={form.name || ''} onChange={e => setForm({ ...form, name: e.target.value })} /></Field>
        {source === 'new' && <Field label="URL поиска HH"><input value={form.searchUrl || ''} onChange={e => setForm({ ...form, searchUrl: e.target.value })} /></Field>}
        <Field label="HH-аккаунт"><Select value={form.hhAccountId || ''} data={accounts.data} label="Выберите аккаунт" onChange={v => setForm({ ...form, hhAccountId: v, resumeId: '' })} /></Field>
        <Field label="Резюме"><Select value={form.resumeId || ''} data={accountResumes.length ? accountResumes : resumes.data} label="Выберите резюме" onChange={v => setForm({ ...form, resumeId: v })} /></Field>
      </div></div>
      <div className="step"><h3>3. Письма</h3><div className="form-grid">
        <Field label="Режим"><select value={normalizeMode(form.coverLetterMode)} onChange={e => setForm({ ...form, coverLetterMode: e.target.value })}><option value="NONE">Без письма</option><option value="COMMON">Одно общее письмо</option><option value="PERSONAL_AI">Персональные ИИ-письма</option></select></Field>
        {normalizeMode(form.coverLetterMode) === 'PERSONAL_AI' && <label className="check"><input type="checkbox" checked={!!form.reviewCoverLettersBeforeApply} onChange={e => setForm({ ...form, reviewCoverLettersBeforeApply: e.target.checked })} /> Проверить письма перед отправкой<small>Если включено, сначала будут сгенерированы все письма. Вы сможете проверить и изменить их, а затем отдельно запустить отклики.</small></label>}
        {normalizeMode(form.coverLetterMode) === 'COMMON' && <Field label="Общее письмо"><textarea value={form.commonCoverLetter || ''} onChange={e => setForm({ ...form, commonCoverLetter: e.target.value })} /></Field>}
      </div></div>
      <div className="step"><h3>4. Ограничения</h3><div className="form-grid">
        <Field label="Максимум откликов"><input type="number" min={0} value={form.maxApplications} onChange={e => setForm({ ...form, maxApplications: Number(e.target.value) })} /></Field>
        <Field label="Задержка, сек"><input type="number" min={0} step="0.5" value={form.delaySeconds} onChange={e => setForm({ ...form, delaySeconds: Number(e.target.value) })} /></Field>
        <Field label="Количество страниц"><input type="number" min={1} max={50} value={form.pages} onChange={e => setForm({ ...form, pages: Number(e.target.value) })} /></Field>
        <Field label="Сколько вакансий загрузить" hint="Пусто = сколько получится."><input type="number" min={0} value={form.vacancyLoadLimit ?? ''} placeholder="без лимита" onChange={e => setForm({ ...form, vacancyLoadLimit: e.target.value })} /></Field>
        <Field label="Include keywords"><input value={form.includeKeywords || ''} onChange={e => setForm({ ...form, includeKeywords: e.target.value })} /></Field>
        <Field label="Exclude keywords"><input value={form.excludeKeywords || ''} onChange={e => setForm({ ...form, excludeKeywords: e.target.value })} /></Field>
      </div></div>
      <div className="toolbar"><button disabled={!canSubmit || create.isPending}>Создать и загрузить вакансии</button>{create.error && <p className="error">{create.error.message}</p>}</div>
    </form>
  </Page>;
}

function CampaignDetailPage() {
  const { id } = useParams();
  const qc = useQueryClient();
  const toast = useToast();
  const me = useMe();
  const [filter, setFilter] = useState<'selected' | 'skipped' | 'applied' | 'all'>('selected');
  const detail = useQuery({
    queryKey: ['campaign', id],
    queryFn: () => api<{ campaign: Campaign; vacancies: Vacancy[] }>(`/api/campaigns/${id}`),
    refetchInterval: q => {
      const c = (q.state.data as { campaign: Campaign } | undefined)?.campaign;
      return c && ['PREVIEW_LOADING', 'LETTERS_GENERATING', 'APPLYING', 'STOPPING'].includes(c.status) ? 3000 : false;
    }
  });
  const c = detail.data?.campaign;
  const vacancies = detail.data?.vacancies ?? [];
  const selected = vacancies.filter(v => v.selected && !v.alreadyApplied);
  const alreadyApplied = vacancies.filter(v => v.alreadyApplied);
  const profileMismatches = selected.filter(v => v.coverLetterStatus === 'PROFILE_MISMATCH');
  const suitable = selected.length;
  const visible = vacancies.filter(v => {
    if (filter === 'all') return true;
    if (filter === 'applied') return v.alreadyApplied;
    if (filter === 'skipped') return !v.selected && !v.alreadyApplied;
    return v.selected && !v.alreadyApplied;
  });
  const mode = normalizeMode(c?.coverLetterMode);
  const canGenerate = !!me.data?.features.coverLetterGenerationEnabled;
  const busy = c ? ['PREVIEW_LOADING', 'LETTERS_GENERATING', 'APPLYING', 'STOPPING'].includes(c.status) : false;
  const readyLetters = selected.filter(v => ['GENERATED', 'EDITED'].includes(v.coverLetterStatus)).length;
  const run = (promise: Promise<unknown>, message: string) => promise.then(() => { toast(message); qc.invalidateQueries({ queryKey: ['campaign', id] }); }).catch(e => toast((e as Error).message));
  const primary = getPrimaryAction(c, selected, readyLetters);
  const canReload = !!c && !busy && ['DRAFT', 'PREVIEW_READY', 'READY_TO_APPLY', 'STOPPED', 'COMPLETED', 'FAILED', 'INTERRUPTED'].includes(c.status);
  function onPrimary() {
    if (!c || !primary) return;
    if (primary.kind === 'preview') run(api(`/api/campaigns/${id}/preview`, { method: 'POST' }), 'Загрузка вакансий запущена');
    if (primary.kind === 'generate') run(api(`/api/campaigns/${id}/cover-letters/generate`, { method: 'POST' }), 'Генерация писем запущена');
    if (primary.kind === 'apply') {
      const letters = mode === 'COMMON' ? 'будет использоваться общее письмо' : mode === 'PERSONAL_AI' && c.reviewCoverLettersBeforeApply ? 'будут использоваться готовые письма' : 'будут генерироваться перед каждым откликом';
      if (confirm(`Начать отправку откликов?\n\nКампания: ${c.name}\nРезюме: ${c.resumeName}\nВакансий выбрано: ${selected.length}\nМаксимум откликов: ${c.maxApplications || 'без лимита'}\nПисьма: ${letters}`)) {
        run(api(`/api/campaigns/${id}/apply`, { method: 'POST' }), 'Отправка откликов запущена');
      }
    }
    if (primary.kind === 'stop') run(api(`/api/campaigns/${id}/stop`, { method: 'POST' }), c.status === 'LETTERS_GENERATING' ? 'Останавливаем генерацию' : 'Прерываем отклики');
  }
  function reloadVacancies() {
    if (!c) return;
    if (confirm(`Заново загрузить вакансии?\n\nКампания: ${c.name}\nСтарые неотправленные вакансии будут заменены. Отправленные и уже отмеченные отклики останутся защищены от повторной отправки.`)) {
      run(api(`/api/campaigns/${id}/vacancies/reload`, { method: 'POST' }), 'Повторная загрузка вакансий запущена');
    }
  }
  function excludeProfileMismatches() {
    if (!c || !profileMismatches.length) return;
    if (confirm(`Убрать не подходящие вакансии из выборки?\n\nБудет исключено: ${profileMismatches.length}`)) {
      run(api(`/api/campaigns/${id}/vacancies/profile-mismatches/exclude`, { method: 'POST' }), 'Не подходящие вакансии исключены');
      setFilter('selected');
    }
  }
  return <Page title={c?.name || 'Кампания'}>
    {c && <div className="campaign-top">
      <div><h3>{c.name}</h3><p>Резюме: {c.resumeName}</p><p>HH-аккаунт: {c.hhAccountName}</p>{c.savedSearchName && <p>Поиск: {c.savedSearchName}</p>}</div>
      <div className="progress-grid"><Card label="Найдено" value={c.totalVacancies || vacancies.length} /><Card label="Уже откликались" value={alreadyApplied.length || c.alreadyCount} /><Card label="Подходят" value={suitable} /><Card label="Писем готово" value={readyLetters} /><Card label="Отправлено" value={c.appliedCount} /><Card label="Ошибок" value={c.failedCount} /></div>
    </div>}
    {c && <div className="action-bar"><span className="badge">{statusLabel[c.status] || c.status}</span>{primary && <button disabled={busy && primary.kind !== 'stop'} onClick={onPrimary}>{primary.label}</button>}{mode === 'PERSONAL_AI' && profileMismatches.length > 0 && <button className="ghost" disabled={busy} onClick={excludeProfileMismatches}>Убрать не подходящие ({profileMismatches.length})</button>}{canReload && <button className="ghost" onClick={reloadVacancies}>Заново загрузить вакансии</button>}{mode === 'PERSONAL_AI' && !c.reviewCoverLettersBeforeApply && <small>Письма будут генерироваться автоматически перед каждым откликом.</small>}</div>}
    {c && <CampaignLetterSettings campaign={c} canGenerate={canGenerate} />}
    {!canGenerate && mode === 'PERSONAL_AI' && <p className="warning">Персональные ИИ-письма недоступны для пользователя. Backend также вернёт 403 при попытке генерации.</p>}
    <div className="toolbar"><span>Показать:</span><button className={filter === 'selected' ? 'selected' : 'ghost'} onClick={() => setFilter('selected')}>Подходящие</button><button className={filter === 'skipped' ? 'selected' : 'ghost'} onClick={() => setFilter('skipped')}>Исключённые</button><button className={filter === 'applied' ? 'selected' : 'ghost'} onClick={() => setFilter('applied')}>Уже откликались</button><button className={filter === 'all' ? 'selected' : 'ghost'} onClick={() => setFilter('all')}>Все</button></div>
    <div className="list">{visible.map(v => <VacancyEditor key={v.id} campaignId={id!} vacancy={v} canGenerate={canGenerate && mode === 'PERSONAL_AI'} />)}</div>
    {!visible.length && <Empty text={filter === 'selected' ? 'Подходящих вакансий нет.' : 'В этом разделе пока пусто.'} />}
  </Page>;
}

function getPrimaryAction(c?: Campaign, selected: Vacancy[] = [], readyLetters = 0) {
  if (!c) return null;
  const mode = normalizeMode(c.coverLetterMode);
  if (c.status === 'DRAFT') return { kind: 'preview', label: 'Загрузить вакансии' };
  if (c.status === 'LETTERS_GENERATING') return { kind: 'stop', label: 'Остановить генерацию' };
  if (c.status === 'APPLYING' || c.status === 'STOPPING') return { kind: 'stop', label: 'Прервать отклики' };
  if (['PREVIEW_READY', 'READY_TO_APPLY', 'STOPPED', 'COMPLETED', 'INTERRUPTED'].includes(c.status)) {
    if (mode === 'PERSONAL_AI' && c.reviewCoverLettersBeforeApply && readyLetters < selected.length) return { kind: 'generate', label: 'Сгенерировать письма' };
    return { kind: 'apply', label: 'Начать отклики' };
  }
  return null;
}

function CampaignLetterSettings({ campaign, canGenerate }: { campaign: Campaign; canGenerate: boolean }) {
  const qc = useQueryClient();
  const toast = useToast();
  const [form, setForm] = useState<any>({});
  useEffect(() => setForm({ coverLetterMode: normalizeMode(campaign.coverLetterMode), commonCoverLetter: campaign.commonCoverLetter || '', reviewCoverLettersBeforeApply: campaign.reviewCoverLettersBeforeApply, delaySeconds: campaign.delaySeconds || 1, maxApplications: campaign.maxApplications || 0 }), [campaign.id, campaign.coverLetterMode, campaign.commonCoverLetter, campaign.reviewCoverLettersBeforeApply, campaign.delaySeconds, campaign.maxApplications]);
  const save = useMutation({ mutationFn: () => api(`/api/campaigns/${campaign.id}/settings`, { method: 'PUT', body: JSON.stringify(form) }), onSuccess: () => { toast('Настройки кампании сохранены'); qc.invalidateQueries({ queryKey: ['campaign', campaign.id] }); } });
  return <div className="item">
    <h3>Сопроводительные письма</h3>
    <div className="form-grid">
      <Field label="Режим"><select value={form.coverLetterMode || 'PERSONAL_AI'} onChange={e => setForm({ ...form, coverLetterMode: e.target.value })}><option value="NONE">Без письма</option><option value="COMMON">Одно общее письмо</option><option value="PERSONAL_AI" disabled={!canGenerate}>Персональное письмо через ИИ</option></select></Field>
      {form.coverLetterMode === 'PERSONAL_AI' && <label className="check"><input type="checkbox" checked={!!form.reviewCoverLettersBeforeApply} onChange={e => setForm({ ...form, reviewCoverLettersBeforeApply: e.target.checked })} /> Проверить письма перед отправкой<small>Если включено, сначала будут сгенерированы все письма. Вы сможете проверить и изменить их, а затем отдельно запустить отклики.</small></label>}
      {form.coverLetterMode === 'COMMON' && <Field label="Общее письмо"><textarea value={form.commonCoverLetter || ''} onChange={e => setForm({ ...form, commonCoverLetter: e.target.value })} /></Field>}
      <Field label="Максимум откликов"><input type="number" min={0} value={form.maxApplications ?? 0} onChange={e => setForm({ ...form, maxApplications: Number(e.target.value) })} /></Field>
      <Field label="Задержка, сек"><input type="number" min={0} step="0.5" value={form.delaySeconds ?? 1} onChange={e => setForm({ ...form, delaySeconds: Number(e.target.value) })} /></Field>
    </div>
    <div className="toolbar"><button disabled={save.isPending} onClick={() => save.mutate()}>Сохранить настройки</button>{save.error && <p className="error">{save.error.message}</p>}</div>
  </div>;
}

function VacancyEditor({ campaignId, vacancy, canGenerate }: { campaignId: string; vacancy: Vacancy; canGenerate: boolean }) {
  const qc = useQueryClient();
  const toast = useToast();
  const [open, setOpen] = useState(false);
  const [letter, setLetter] = useState(vacancy.coverLetter || '');
  useEffect(() => setLetter(vacancy.coverLetter || ''), [vacancy.coverLetter]);
  const patchCache = (selected: boolean) => qc.setQueryData(['campaign', campaignId], (old: any) => old ? { ...old, vacancies: old.vacancies.map((v: Vacancy) => v.id === vacancy.id ? { ...v, selected, applyStatus: selected ? 'PENDING' : 'SKIPPED', coverLetterStatus: selected ? 'PENDING' : 'SKIPPED' } : v) } : old);
  const run = (promise: Promise<unknown>, message: string) => promise.then(() => { toast(message); qc.invalidateQueries({ queryKey: ['campaign', campaignId] }); }).catch(e => { toast((e as Error).message); qc.invalidateQueries({ queryKey: ['campaign', campaignId] }); });
  function toggleSelected(selected: boolean) {
    patchCache(selected);
    run(api(`/api/campaigns/${campaignId}/vacancies/${vacancy.id}`, { method: 'PUT', body: JSON.stringify({ selected }) }), selected ? 'Вакансия возвращена' : 'Вакансия исключена');
  }
  const sent = ['SENT', 'ALREADY_APPLIED'].includes(vacancy.applyStatus) || vacancy.alreadyApplied;
  const error = vacancy.generationError || vacancy.applyErrorCode;
  const visibleStatus = vacancy.applyStatus === 'PENDING'
    ? (letterStatusLabel[vacancy.coverLetterStatus] || 'В выборке')
    : (applyStatusLabel[vacancy.applyStatus] || letterStatusLabel[vacancy.coverLetterStatus] || 'В работе');
  return <div className="vacancy-card">
    <div className="vacancy-main">
      <label className="vacancy-check" title={vacancy.alreadyApplied ? 'Отклик уже был отправлен ранее' : vacancy.selected ? 'Вакансия в выборке' : 'Вакансия исключена'}>
        <input type="checkbox" checked={vacancy.selected} disabled={sent} onChange={e => toggleSelected(e.target.checked)} />
        <span>{vacancy.alreadyApplied ? 'Уже откликались' : vacancy.selected ? 'В выборке' : 'Исключена'}</span>
      </label>
      <div><h3>{vacancy.title}</h3><p>Компания: {vacancy.companyName || 'не указана'}</p><p>Статус: {visibleStatus}</p></div>
      <div className="toolbar"><a className="button-link ghost-link" href={vacancy.vacancyUrl} target="_blank">Открыть HH</a><button className="ghost" onClick={() => setOpen(!open)}>{open ? 'Скрыть' : 'Показать описание'}</button></div>
    </div>
    {error && <p className="error">{safeError(error)} <small>Код: {error}</small></p>}
    {open && <div className="vacancy-details">
      {vacancy.description && <p>{vacancy.description}</p>}
      <Field label="Письмо"><textarea value={letter} onChange={e => setLetter(e.target.value)} placeholder="Сопроводительное письмо" /></Field>
      <div className="toolbar">
        <button onClick={() => run(api(`/api/campaigns/${campaignId}/vacancies/${vacancy.id}/cover-letter`, { method: 'PUT', body: JSON.stringify({ coverLetter: letter }) }), 'Письмо сохранено')}>Сохранить письмо</button>
        <button disabled={!canGenerate} onClick={() => run(api(`/api/campaigns/${campaignId}/vacancies/${vacancy.id}/cover-letter/regenerate`, { method: 'POST' }), 'Перегенерация запущена')}>Перегенерировать</button>
        {vacancy.selected ? <button className="ghost" disabled={sent} onClick={() => toggleSelected(false)}>Исключить</button> : <button onClick={() => toggleSelected(true)}>Вернуть</button>}
      </div>
    </div>}
  </div>;
}

function AdminUsersPage() {
  const qc = useQueryClient();
  const users = useQuery({ queryKey: ['admin-users'], queryFn: () => api<User[]>('/api/admin/users') });
  return <Page title="Пользователи"><div className="list">{users.data?.map(u => <div className="row" key={u.id}><span>{u.email}</span><small>{u.status}</small><label><input type="checkbox" checked={u.features.coverLetterGenerationEnabled} onChange={e => api(`/api/admin/users/${u.id}/features`, { method: 'PATCH', body: JSON.stringify({ coverLetterGenerationEnabled: e.target.checked }) }).then(() => qc.invalidateQueries({ queryKey: ['admin-users'] }))} /> ИИ</label><button onClick={() => api(`/api/admin/users/${u.id}/status`, { method: 'PATCH', body: JSON.stringify({ status: u.status === 'ACTIVE' ? 'BLOCKED' : 'ACTIVE' }) }).then(() => qc.invalidateQueries({ queryKey: ['admin-users'] }))}>{u.status === 'ACTIVE' ? 'Заблокировать' : 'Активировать'}</button></div>)}</div></Page>;
}

function App() {
  return <QueryClientProvider client={queryClient}><ToastProvider><BrowserRouter><Routes>
    <Route path="/" element={<Navigate to="/dashboard" replace />} />
    <Route path="/login" element={<AuthPage mode="login" />} />
    <Route path="/register" element={<AuthPage mode="register" />} />
    <Route path="/dashboard" element={<Protected><Dashboard /></Protected>} />
    <Route path="/hh-accounts" element={<Protected><HhAccountsPage /></Protected>} />
    <Route path="/resumes" element={<Protected><ResumesPage /></Protected>} />
    <Route path="/searches" element={<Protected><SearchesPage /></Protected>} />
    <Route path="/searches/new" element={<Protected><SearchFormPage /></Protected>} />
    <Route path="/searches/:id/edit" element={<Protected><SearchFormPage /></Protected>} />
    <Route path="/campaigns" element={<Protected><CampaignsPage /></Protected>} />
    <Route path="/campaigns/new" element={<Protected><NewCampaignPage /></Protected>} />
    <Route path="/campaigns/:id" element={<Protected><CampaignDetailPage /></Protected>} />
    <Route path="/admin/users" element={<Protected><AdminUsersPage /></Protected>} />
  </Routes></BrowserRouter></ToastProvider></QueryClientProvider>;
}

ReactDOM.createRoot(document.getElementById('root')!).render(<App />);
