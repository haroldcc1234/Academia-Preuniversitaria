export const BASE=import.meta.env.VITE_API_URL||'/api';
export function token(){return sessionStorage.getItem('academia-token')||''}
export async function api(path,method='GET',body){const res=await fetch(BASE+path,{method,headers:{...(token()?{Authorization:'Bearer '+token()}:{}),...(body&&!(body instanceof FormData)?{'Content-Type':'application/json'}:{})},body:body?(body instanceof FormData?body:JSON.stringify(body)):undefined});if(res.status===401&&!path.includes('/auth/login'))window.dispatchEvent(new Event('session-expired'));if(!res.ok){const e=await res.json().catch(()=>({}));throw new Error(e.error||e.message||'No se pudo completar la operación.');}return res.status===204?null:res.json();}
export async function download(path,name='reporte.pdf'){const res=await fetch(BASE+path,{headers:{Authorization:'Bearer '+token()}});if(!res.ok){const e=await res.json().catch(()=>({}));throw new Error(e.error||'No se pudo descargar');}const url=URL.createObjectURL(await res.blob());const a=document.createElement('a');a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}
export const fnum=(n,d=1)=>n==null?'—':Number(n).toFixed(d);
export const date=s=>s?new Date(s.length===10?s+'T12:00:00':s).toLocaleDateString('es-PE'):'—';
export const datetime=s=>s?new Date(s).toLocaleString('es-PE',{timeZone:'America/Lima'}):'—';
export const parse=(s,f={})=>{try{return typeof s==='string'?JSON.parse(s):s||f}catch{return f}};
