/* バックエンド(/api)との連携。app.js の画面ロジックを、サーバー保存版の関数で上書きします。 */
const TK = 'hairia_token';
async function api(path, method = 'GET', body) {
  const h = {'Content-Type': 'application/json'}, t = localStorage.getItem(TK);
  if (t) h.Authorization = 'Bearer ' + t;
  const r = await fetch('/api' + path, {method, headers: h, body: body === undefined ? undefined : JSON.stringify(body)});
  let d = null; try { d = await r.json(); } catch (e) {}
  if (r.status == 401 && t && !path.startsWith('/auth')) { localStorage.removeItem(TK); S.role = null; go('wc'); }
  if (!r.ok) throw new Error((d && d.message) || 'エラーが発生しました');
  return d;
}
const run = async f => { try { await f(); } catch (e) { T(e.message); } };
async function load() {
  const [me, cust, sy, kt, msg, rs, rms, log] = await Promise.all(
    ['/me', '/customers', '/hairdressers', '/charts', '/chat/messages', '/reservations', '/roadmaps', '/audit'].map(p => api(p)));
  S.uid = me.id; S.role = me.role == 'STYLIST' ? 's' : 'c';
  S.cust = cust; SY = sy; S.kt = kt; S.msg = msg;
  S.rs = rs.filter(r => r.st != 'キャンセル'); S.bk = rs.map(r => ({id: r.id, d: r.d, s: r.st}));
  S.rms = rms; S.log = log; S.ai_ok = me.aiOk !== false; S.ext = !!me.ext;
  if (S.role == 's' && !C(S.cur)) S.cur = S.cust[0] ? S.cust[0].id : 0;
  syncRm();
}
function syncRm() { const r = S.rms[S.role == 's' ? S.cur : S.uid]; S.rm = r ? r.steps : []; S.rmSh = r ? r.shared : false; }
function go(p, a) {
  S.page = p; S.arg = a; syncRm(); render();
  if (p == 'ch' && a) api('/chat/read', 'POST', {withId: a}).then(load).then(() => { if (S.page == 'ch' && S.arg == a) render(); }).catch(() => {});
}
async function enter() { await load(); go(S.role == 's' ? 'dash' : 'hm'); }
async function login() {
  await run(async () => {
    const d = await api('/auth/login', 'POST', {email: $('#le').value, password: $('#lp').value});
    localStorage.setItem(TK, d.token); await enter();
  });
}
async function signup() {
  await run(async () => {
    const d = await api('/auth/signup', 'POST', {name: $('#rn').value, email: $('#re').value, password: $('#rp').value});
    localStorage.setItem(TK, d.token); await enter();
  });
}
function out() { api('/auth/logout', 'POST').catch(() => {}); localStorage.removeItem(TK); S.role = null; S.cust = []; S.kt = []; S.msg = []; go('wc'); }
const saveFields = async i => { const k = K(i); await api('/charts/' + i, 'PUT', {plan: k.plan, act: k.act, dr: k.dr, memo: k.memo}); };
async function saveK(i, toast) { await run(async () => { await saveFields(i); await load(); if (toast) T('下書きを保存しました'); render(); }); }
async function adv(i) { await run(async () => { await saveFields(i); await api('/charts/' + i + '/advance', 'POST'); await load(); render(); }); }
function fin(i) { return adv(i); }
async function knew() {
  await run(async () => {
    const d = await api('/charts', 'POST', {customerId: S.cur, menu: 'カラー', plan: `${S.in.col}（実現可能性 ${S.ai ? S.ai.lv : '-'}）`});
    await load(); go('ke', d.id);
  });
}
async function share(i) {
  await run(async () => {
    const s1 = $('#s1'), s2 = $('#s2'), cid = i ? K(i).c : S.cur;
    if (i && s1 && s1.checked) await api('/charts/' + i + '/share', 'POST');
    if (s2 && s2.checked) await api('/roadmaps/' + cid + '/share', 'POST');
    await load(); T('お客様の画面に反映されました'); go('cd', cid);
  });
}
async function ana() {
  await run(async () => {
    T('分析中…');
    S.ai = await api('/counselings/analysis', 'POST', {customerId: S.cur, type: S.ct, ...S.in});
    S.draft = null; await load(); go('ai');
  });
}
async function send(t) {
  const v = t || $('#mi').value.trim(); if (!v) return;
  await run(async () => { await api('/chat/messages', 'POST', {withId: S.arg, body: v}); await load(); render(); });
}
function shrink(f, max, cb) {
  const r = new FileReader();
  r.onload = () => { const i = new Image(); i.onload = () => { const k = Math.min(1, max / Math.max(i.width, i.height)), c = document.createElement('canvas');
    c.width = i.width * k; c.height = i.height * k; c.getContext('2d').drawImage(i, 0, 0, c.width, c.height); cb(c.toDataURL('image/jpeg', .8)); }; i.src = r.result; };
  r.readAsDataURL(f);
}
function sendImg(el) {
  const f = el.files[0]; if (!f) return;
  shrink(f, 900, u => run(async () => { await api('/chat/messages', 'POST', {withId: S.arg, image: u}); await load(); render(); }));
}
function pick(el, o) { const f = el.files[0]; if (f) shrink(f, 300, u => { o.av = u; render(); }); }
async function book() {
  if (!S.slot) return T('日時を選択してください');
  const sid = Object.keys(SY)[0]; if (!sid) return T('先に美容師と連携してください');
  await run(async () => {
    await api('/reservations', 'POST', {stylistId: +sid, slot: '2026-' + S.slot.replace('/', '-').replace(' ', 'T')});
    S.slot = ''; await load(); render();
  });
}
async function cancelRs(id) { await run(async () => { await api('/reservations/' + id + '/cancel', 'POST'); await load(); render(); }); }
async function svs() { await run(async () => { await api('/questionnaires/answers', 'POST', S.svd); await load(); T('送信しました'); go('hm'); }); }
async function saveMe(toast) {
  const m = S.role == 's' ? SY[S.uid] : C(S.uid);
  await run(async () => {
    await api('/me', 'PUT', {name: m.n, gender: m.g, shop: m.shop, av: m.av, cons: S.role == 'c' ? m.cons : undefined, aiOk: S.ai_ok, ext: S.ext});
    if (toast) T('保存しました');
  });
}
async function newQr() { await run(async () => { const d = await api('/qr', 'POST'); S.tok = d.token; S.qr = 1; render(); }); }
async function redeem() { await run(async () => { await api('/qr/redeem', 'POST', {token: $('#qc').value}); await load(); T('連携しました'); render(); }); }
(async () => { if (localStorage.getItem(TK)) { try { await enter(); } catch (e) {} } })();
setInterval(async () => { if (S.role && S.page == 'ch' && !S.arg) { try { await load(); render(); } catch (e) {} } }, 10000);
