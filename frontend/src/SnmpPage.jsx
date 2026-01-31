import { useState, useEffect, useCallback } from 'react';

const C06_URL = import.meta.env.VITE_C06_URL || 'http://localhost:3000';
const REFRESH_INTERVAL_MS = 15000;

export default function SnmpPage() {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [lastFetch, setLastFetch] = useState(null);

  const fetchSnmp = useCallback(async () => {
    setError(null);
    try {
      const res = await fetch(`${C06_URL}/api/snmp`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const json = await res.json();
      setData(Array.isArray(json) ? json : []);
      setLastFetch(new Date());
    } catch (e) {
      setError(e.message || 'Failed to load SNMP data');
      setData([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchSnmp();
  }, [fetchSnmp]);

  useEffect(() => {
    const id = setInterval(fetchSnmp, REFRESH_INTERVAL_MS);
    return () => clearInterval(id);
  }, [fetchSnmp]);

  const formatDate = (ts) => {
    if (!ts) return '—';
    const d = new Date(ts);
    return d.toLocaleString();
  };

  const byNode = {};
  data.forEach((row) => {
    if (!byNode[row.node]) byNode[row.node] = [];
    byNode[row.node].push(row);
  });
  const nodes = Object.keys(byNode).sort();
  const latestByNode = nodes.map((node) => {
    const rows = byNode[node].sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp));
    return { node, ...rows[0] };
  });

  return (
    <div className="app snmp-page">
      <h1>SNMP Monitor</h1>
      <p className="subtitle">
        OS name, CPU and RAM usage collected from all nodes (C01–C06). Auto-refresh every {REFRESH_INTERVAL_MS / 1000}s.
      </p>

      {loading && <p className="snmp-loading">Loading…</p>}
      {error && <div className="error-msg">{error}</div>}

      <div className="snmp-actions">
        <button type="button" className="btn btn-secondary" onClick={fetchSnmp} disabled={loading}>
          Refresh now
        </button>
        {lastFetch && (
          <span className="snmp-last">Last update: {formatDate(lastFetch)}</span>
        )}
      </div>

      {!loading && !error && (
        <div className="snmp-table-wrap">
          <table className="snmp-table">
            <thead>
              <tr>
                <th>Node</th>
                <th>OS name</th>
                <th>CPU %</th>
                <th>RAM %</th>
                <th>Timestamp</th>
              </tr>
            </thead>
            <tbody>
              {latestByNode.length === 0 ? (
                <tr>
                  <td colSpan={5} className="snmp-empty">No data yet. Ensure C06 collector is running.</td>
                </tr>
              ) : (
                latestByNode.map((row) => (
                  <tr key={`${row.node}-${row.timestamp}`}>
                    <td className="snmp-node">{row.node}</td>
                    <td className="snmp-os">{row.osName || '—'}</td>
                    <td className="snmp-cpu">{typeof row.cpuUsage === 'number' ? row.cpuUsage.toFixed(2) : row.cpuUsage}</td>
                    <td className="snmp-ram">{typeof row.ramUsage === 'number' ? row.ramUsage.toFixed(2) : row.ramUsage}</td>
                    <td className="snmp-ts">{formatDate(row.timestamp)}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}

      {!loading && data.length > 0 && (
        <p className="snmp-hint">Showing latest value per node (total {data.length} records in response).</p>
      )}
    </div>
  );
}
