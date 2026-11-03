import React, { useState, useEffect, useRef } from 'react';
import { X, LoaderCircle, Search, Inbox, ArrowUpRight } from 'lucide-react';
import { api } from './api';

export function useLoad(path, deps = []) {
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  async function reload() {
    if (!path) {
      setData([]);
      setLoading(false);
      return;
    }
    setLoading(true);
    try {
      setData(await api(path));
      setError('');
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (!path) {
      setData([]);
      setLoading(false);
      return;
    }
    let live = true;
    setLoading(true);
    api(path)
      .then(d => {
        if (live) {
          setData(d);
          setError('');
        }
      })
      .catch(e => {
        if (live) setError(e.message);
      })
      .finally(() => {
        if (live) setLoading(false);
      });
    return () => {
      live = false;
    };
  }, [path, ...deps]);

  return { data, error, loading, reload, setData };
}

export function Loading() {
  return (
    <div className="loading">
      <LoaderCircle className="spin" /> Cargando información…
    </div>
  );
}

export function Empty({ text = 'Todavía no hay registros.', children }) {
  return (
    <div className="empty">
      <Inbox size={34} />
      <h3>{text}</h3>
      {children}
    </div>
  );
}

export function ErrorBox({ error }) {
  return error ? (
    <div role="alert" className="errorbox">
      {error}
    </div>
  ) : null;
}

export function Badge({ children, tone = '' }) {
  return <span className={'badge ' + tone}>{children}</span>;
}

export function PageHead({ eyebrow = 'CAMPUS ACADÉMICO', title, description, children }) {
  return (
    <div className="page-head">
      <div>
        <p className="eyebrow">{eyebrow}</p>
        <h1>{title}</h1>
        {description && <p className="muted">{description}</p>}
      </div>
      <div className="actions">{children}</div>
    </div>
  );
}

export function Button({ children, variant = '', className = '', ...props }) {
  return (
    <button className={'btn ' + variant + ' ' + className} {...props}>
      {children}
    </button>
  );
}

export function Modal({ title, children, onClose, wide = false }) {
  const ref = useRef();

  useEffect(() => {
    ref.current.showModal();
    return () => {
      ref.current?.close();
    };
  }, []);

  return (
    <dialog
      ref={ref}
      className={'modal ' + (wide ? 'wide' : '')}
      onCancel={e => {
        e.preventDefault();
        onClose();
      }}
      onClick={e => {
        if (e.target === ref.current) onClose();
      }}
    >
      <div className="modal-head">
        <h2>{title}</h2>
        <button className="icon-btn" aria-label="Cerrar" onClick={onClose}>
          <X />
        </button>
      </div>
      {children}
    </dialog>
  );
}

export function Field({ label, name, value, onChange, type = 'text', options, required = false, ...props }) {
  return (
    <label className="field">
      <span>
        {label}
        {required ? ' *' : ''}
      </span>
      {type === 'textarea' ? (
        <textarea
          name={name}
          value={value ?? ''}
          onChange={e => onChange(e.target.value)}
          required={required}
          {...props}
        />
      ) : type === 'select' ? (
        <select
          name={name}
          value={value ?? ''}
          onChange={e => onChange(e.target.value)}
          required={required}
          {...props}
        >
          <option value="">Seleccionar…</option>
          {(options || []).map(o => (
            <option key={typeof o === 'string' ? o : o.value} value={typeof o === 'string' ? o : o.value}>
              {typeof o === 'string' ? o : o.label}
            </option>
          ))}
        </select>
      ) : type === 'checkbox' ? (
        <input type="checkbox" checked={!!value} onChange={e => onChange(e.target.checked)} {...props} />
      ) : (
        <input
          name={name}
          type={type}
          value={value ?? ''}
          onChange={e => onChange(e.target.value)}
          required={required}
          {...props}
        />
      )}
    </label>
  );
}

export function FormDialog({ title, fields, initial = {}, onSave, onClose, children }) {
  const [form, setForm] = useState(initial);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    try {
      await onSave(form);
      onClose();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal title={title} onClose={onClose}>
      <form onSubmit={submit}>
        <div className="form-grid">
          {fields.map(f => (
            <Field key={f.name} {...f} value={form[f.name]} onChange={v => setForm({ ...form, [f.name]: v })} />
          ))}
        </div>
        {typeof children === 'function' ? children(form, setForm) : children}
        <ErrorBox error={error} />
        <div className="form-actions">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancelar
          </Button>
          <Button disabled={busy}>{busy ? 'Guardando…' : 'Guardar'}</Button>
        </div>
      </form>
    </Modal>
  );
}

export function DataTable({ columns, rows, empty = 'No hay registros para mostrar.' }) {
  if (!rows?.length) return <Empty text={empty} />;
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            {columns.map((c, i) => (
              <th key={i}>{c.label}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={r.id || i}>
              {columns.map((c, j) => (
                <td key={j}>{c.render ? c.render(r) : r[c.key] ?? '—'}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function SearchBox({ value, onChange, placeholder = 'Buscar…' }) {
  return (
    <label className="search">
      <Search size={18} />
      <input aria-label={placeholder} placeholder={placeholder} value={value} onChange={e => onChange(e.target.value)} />
    </label>
  );
}

export function Panel({ title, children, action, className = '' }) {
  return (
    <section className={'panel ' + className}>
      {title && (
        <div className="panel-title">
          <h2>{title}</h2>
          {action}
        </div>
      )}
      {children}
    </section>
  );
}

export const option = (list, label = 'name') =>
  (list || []).map(x => ({
    value: x.id,
    label: typeof label === 'function' ? label(x) : x[label],
  }));