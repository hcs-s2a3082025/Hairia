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
const esc = s => String(s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
const un = s => String(s ?? '').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/&amp;/g, '&');
const IMGRE = /^data:image\/(jpeg|png|gif|webp);base64,[A-Za-z0-9+/=]+$/;
/* サーバーから受け取った文字列はHTMLとして解釈されないよう無害化する（画像は安全な形式のみ通す）。保存時は un() で元に戻す。 */
const E = o => typeof o == 'string' ? esc(o) : Array.isArray(o) ? o.map(E)
  : o && typeof o == 'object' ? Object.fromEntries(Object.entries(o).map(([k, v]) => [k, k == 'av' || k == 'img' ? (typeof v == 'string' && IMGRE.test(v) ? v : (k == 'img' ? null : '')) : E(v)])) : o;
async function load() {
  const [me, cust, sy, kt, msg, rs, rms, log, nf] = await Promise.all(
    ['/me', '/customers', '/hairdressers', '/charts', '/chat/messages', '/reservations', '/roadmaps', '/audit', '/notifications'].map(p => api(p)));
  S.uid = me.id; S.role = me.role == 'STYLIST' ? 's' : 'c';
  S.cust = E(cust); SY = E(sy); S.kt = E(kt); S.msg = E(msg);
  S.rs = rs.filter(r => r.st != 'キャンセル'); S.bk = rs.map(r => ({id: r.id, d: r.d, s: r.st}));
  S.rms = E(rms); S.log = E(log); S.nf = E(nf); S.ai_ok = me.aiOk !== false; S.ext = !!me.ext;
  if (S.role == 's' && !C(S.cur)) S.cur = S.cust[0] ? S.cust[0].id : 0;
  syncRm();
}
function syncRm() { const r = S.rms[S.role == 's' ? S.cur : S.uid]; S.rm = r ? r.steps : []; S.rmSh = r ? r.shared : false; }
function go(p, a) {
  S.page = p; S.arg = a; syncRm(); render();
  if (p == 'ch' && a) api('/chat/read', 'POST', {withId: a}).then(load).then(() => { if (S.page == 'ch' && S.arg == a) render(); }).catch(() => {});
}
async function enter() {
  await load(); go(S.role == 's' ? 'dash' : 'hm');
  const pl = sessionStorage.getItem('pendingLink');
  if (pl) { sessionStorage.removeItem('pendingLink'); if (S.role == 'c') await redeemCode(pl); else T('連携コードはお客様のアカウントで読み取ってください'); }
}
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
const saveFields = async i => { const k = K(i); await api('/charts/' + i, 'PUT', {plan: un(k.plan), act: un(k.act), dr: un(k.dr), memo: un(k.memo)}); };
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
    await api('/me', 'PUT', {name: un(m.n), gender: m.g, shop: un(m.shop), av: m.av, cons: S.role == 'c' ? m.cons : undefined, aiOk: S.ai_ok, ext: S.ext});
    if (toast) T('保存しました');
  });
}

/* ---- QRコード表示（美容師）・読み取り（お客様） ---- */
let qrTimer = 0, scanStream = null, scanRaf = 0;
const qrUrl = c => location.origin + '/?link=' + c;
function qrm() {
  let svg = ''; try { const q = qrcode(0, 'M'); q.addData(qrUrl(S.tok)); q.make(); svg = q.createSvgTag(5, 0); } catch (e) {}
  return `<div class="mo" onclick="closeQr()"><div class="card ct qc" onclick="event.stopPropagation()"><a class="x" onclick="closeQr()">✕</a><h2>お客様連携QR</h2><div class="qrs">${svg}</div><small>連携コード　<b style="letter-spacing:3px">${S.tok}</b><br>有効期限 <span id="qct">--:--</span>・1回のみ使用可<br>（QRに個人情報は含まれません）</small><button class="btn ol" onclick="newQr()">QRを更新</button></div></div>`;
}
function closeQr() { S.qr = 0; clearInterval(qrTimer); render(); }
function qrTick() { const e = $('#qct'); if (!e) return; const s = Math.max(0, Math.round((S.qrExp - Date.now()) / 1000)); e.textContent = s ? `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}` : '期限切れ（更新してください）'; }
async function newQr() {
  await run(async () => { const d = await api('/qr', 'POST'); S.tok = d.token; S.qrExp = Date.now() + (d.expiresIn || 600) * 1000; S.qr = 1; render(); clearInterval(qrTimer); qrTimer = setInterval(qrTick, 1000); qrTick(); });
}
const scm = () => `<div class="mo" onclick="stopScan()"><div class="card ct qc" onclick="event.stopPropagation()"><a class="x" onclick="stopScan()">✕</a><h2>QRを読み取る</h2><video id="sv" playsinline muted></video><canvas id="sc" hidden></canvas><small id="sm">美容師のQRコードを枠に合わせてください</small><label class="fl" style="margin-top:12px">画像から読み取る<input type="file" accept="image/*" hidden onchange="scanFile(this)"></label></div></div>`;
async function startScan() {
  S.scan = 1; render();
  try { scanStream = await navigator.mediaDevices.getUserMedia({video: {facingMode: 'environment'}}); const v = $('#sv'); v.srcObject = scanStream; await v.play(); scanLoop(); }
  catch (e) { const m = $('#sm'); if (m) m.textContent = 'カメラを使えません。下の「画像から読み取る」をお使いください。'; }
}
function scanLoop() {
  const v = $('#sv'), c = $('#sc'); if (!S.scan || !v) return;
  if (v.readyState >= 2 && v.videoWidth) {
    c.width = v.videoWidth; c.height = v.videoHeight; const x = c.getContext('2d', {willReadFrequently: true}); x.drawImage(v, 0, 0);
    const d = x.getImageData(0, 0, c.width, c.height), r = jsQR(d.data, d.width, d.height);
    if (r && r.data) { gotCode(r.data); return; }
  }
  scanRaf = requestAnimationFrame(scanLoop);
}
function stopScan() { S.scan = 0; cancelAnimationFrame(scanRaf); if (scanStream) { scanStream.getTracks().forEach(t => t.stop()); scanStream = null; } render(); }
function codeFrom(t) { try { return new URL(t).searchParams.get('link') || t; } catch (e) { return t; } }
async function gotCode(t) { stopScan(); await redeemCode(codeFrom(t)); }
function scanFile(el) {
  const f = el.files[0]; if (!f) return; const i = new Image(), r = new FileReader();
  r.onload = () => { i.onload = () => { const k = Math.min(1, 1400 / Math.max(i.width, i.height)), c = document.createElement('canvas'); c.width = i.width * k; c.height = i.height * k;
    const x = c.getContext('2d'); x.drawImage(i, 0, 0, c.width, c.height); const d = x.getImageData(0, 0, c.width, c.height), q = jsQR(d.data, d.width, d.height);
    if (q && q.data) gotCode(q.data); else T('QRコードを読み取れませんでした'); }; i.src = r.result; };
  r.readAsDataURL(f);
}
async function redeemCode(code) { await run(async () => { await api('/qr/redeem', 'POST', {token: code}); await load(); T('美容師と連携しました'); render(); }); }
function redeem() { return redeemCode($('#qc').value); }
async function unlink(id) { if (!confirm('この美容師との連携を解除しますか？')) return; await run(async () => { await api('/links/' + id + '/end', 'POST'); await load(); T('連携を解除しました'); render(); }); }
async function withdraw() { if (!confirm('退会するとログインできなくなります。よろしいですか？')) return; await run(async () => { await api('/me/withdraw', 'POST'); localStorage.removeItem(TK); S.role = null; T('退会しました'); go('wc'); }); }

/* ---- 通知 ---- */
I.bell = '<path d="M6 9a6 6 0 0 1 12 0c0 6 2 7 2 7H4s2-1 2-7M10 20a2 2 0 0 0 4 0"/>';
const bellh = () => { const n = (S.nf || []).filter(x => !x.read).length; return `<a class="bell" onclick="openNf()">${ic('bell', 22)}${n ? `<i>${n}</i>` : ''}</a>`; };
const NFGO = {CHAT: 'ch', CHART_SHARED: 'kl', ROADMAP_SHARED: 'rm', RESERVATION: 'dash', SURVEY: 'cl', LINK: 'cl'};
function nfGo(t) { let p = NFGO[t] || 'dash'; if (S.role == 'c' && (p == 'dash' || p == 'cl')) p = 'hm'; go(p); }
async function openNf() {
  S.nfNew = (S.nf || []).filter(x => !x.read).map(x => x.id); go('nf');
  try { await api('/notifications/read', 'POST'); (S.nf || []).forEach(x => x.read = true); render(); } catch (e) {}
}
P.nf = () => hd('通知', [S.role == 's' ? 'dash' : 'hm']) + `<div class="card">${(S.nf || []).map(x => `<div class="li" onclick="nfGo('${x.t}')"><div><b style="font-weight:${(S.nfNew || []).includes(x.id) ? 700 : 400}">${x.title}</b><small>${x.body ? x.body + '　' : ''}${x.tm}</small></div></div>`).join('') || '<small>通知はありません</small>'}</div>`;

/* ---- パスワード再設定 ---- */
P.reset = () => hd('パスワード再設定', ['login']) + `<div class="card"><label style="margin-top:0">登録メールアドレス</label><input id="rs1" placeholder="hairia@example.com"></div>${S.rr ? `<div class="nt">開発モードのため、メールの代わりにここへ再設定リンクを表示します。<br><a onclick="S.rt='${S.rr}';go('reset2')">新しいパスワードを設定する</a></div>` : ''}<button class="btn" onclick="resetReq()">再設定の案内を送る</button>`;
P.reset2 = () => hd('新しいパスワード', ['login']) + `<div class="card"><label style="margin-top:0">新しいパスワード（8文字以上）</label><input id="np" type="password"><label>確認</label><input id="np2" type="password"></div><button class="btn" onclick="resetDo()">変更する</button>`;
async function resetReq() { await run(async () => { const d = await api('/auth/password-reset/request', 'POST', {email: $('#rs1').value}); S.rr = d.token || ''; T('再設定の手続きを受け付けました'); render(); }); }
async function resetDo() {
  if ($('#np').value != $('#np2').value) return T('パスワードが一致しません');
  await run(async () => { await api('/auth/password-reset/confirm', 'POST', {token: S.rt, password: $('#np').value}); S.rt = ''; T('パスワードを変更しました。ログインしてください'); go('login'); });
}

/* ---- ロードマップ（美容師は修正可能） ---- */
P.rm = () => {
  const s = S.role == 's', st = S.rm, v = x => x == null ? '' : x;
  const view = `<div class="card tl">${st.map(x => `<div><b>${v(x.w)}　${v(x.t)}</b><small>期待される変化：${v(x.e)}<br>注意：${v(x.n)}</small></div>`).join('')}</div>`;
  if (!s) return hd('髪質改善ロードマップ', ['hm']) + (S.rmSh ? view + `<button class="btn" onclick="go('rv')">次回予約へ</button><button class="btn ol" onclick="go('sim')">髪色シミュレーション</button>` : '<div class="card"><small>ロードマップはまだ共有されていません。</small></div>');
  return hd('ロードマップ', ['cd', S.cur]) + '<div class="nt">ロードマップは提案です。内容を確認・修正してから共有してください。修正すると、再共有するまでお客様には表示されません。</div>'
    + (st.length ? st.map((x, i) => `<div class="card"><label style="margin-top:0">時期</label><input value="${v(x.w)}" oninput="S.rm[${i}].w=this.value"><label>内容</label><input value="${v(x.t)}" oninput="S.rm[${i}].t=this.value"><label>期待される変化</label><input value="${v(x.e)}" oninput="S.rm[${i}].e=this.value"><label>注意</label><input value="${v(x.n)}" oninput="S.rm[${i}].n=this.value"></div>`).join('')
      + `<button class="btn ol" onclick="saveRm()">修正を保存</button><button class="btn" onclick="go('sh','rm')">共有内容の確認へ</button>`
      : '<div class="card"><small>ロードマップがありません。カウンセリングの分析を実行してください。</small></div>')
    + `<button class="btn ol" onclick="go('sim')">シミュレーション</button>`;
};
async function saveRm() {
  await run(async () => { await api('/roadmaps/' + S.cur + '/steps', 'PUT', S.rm.map(x => ({w: un(x.w), t: un(x.t), e: un(x.e), n: un(x.n)}))); await load(); T('修正を保存しました（再共有が必要です）'); render(); });
}

(function () {
  const p = new URLSearchParams(location.search), l = p.get('link'), r = p.get('reset');
  if (l) sessionStorage.setItem('pendingLink', l.toUpperCase().slice(0, 16));
  if (r) { S.rt = r; S.page = 'reset2'; }
  if (l || r) history.replaceState(null, '', location.pathname);
  if (!localStorage.getItem(TK) && l) { S.page = 'login'; setTimeout(() => T('ログインまたは新規登録すると、美容師と連携できます'), 300); }
  render();
})();
(async () => { if (localStorage.getItem(TK)) { try { await enter(); } catch (e) {} } })();
setInterval(async () => { if (S.role && !S.scan && !S.qr && (S.page == 'dash' || S.page == 'hm' || (S.page == 'ch' && !S.arg))) { try { await load(); render(); } catch (e) {} } }, 10000);
