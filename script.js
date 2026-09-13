const form = document.getElementById('txForm');
const submitBtn = document.getElementById('submitBtn');
const errorBox = document.getElementById('errorBox');
const emptyState = document.getElementById('emptyState');
const stepsEl = document.getElementById('steps');
const resultCard = document.getElementById('resultCard');

let pollTimer = null;

function showError(msg) {
    errorBox.textContent = msg;
    errorBox.style.display = 'block';
}
function clearError() {
    errorBox.style.display = 'none';
    errorBox.textContent = '';
}

function setStep(name, state) {
    const el = document.querySelector(`.step[data-step="${name}"]`);
    el.classList.remove('active', 'done', 'flagged');
    if (state) el.classList.add(state);
}
function setDetail(name, text) {
    document.getElementById('detail-' + name).textContent = text;
}

function resetPipeline() {
    ['submitted', 'queued', 'scored', 'final'].forEach(s => { setStep(s, null); setDetail(s, '—'); });
    resultCard.classList.remove('show');
}

form.addEventListener('submit', async (e) => {
    e.preventDefault();
    clearError();
    if (pollTimer) clearInterval(pollTimer);
    resetPipeline();

    const apiBase = document.getElementById('apiBase').value.replace(/\/$/, '');
    const payload = {
        accountId: document.getElementById('accountId').value,
        amount: parseFloat(document.getElementById('amount').value),
        currency: document.getElementById('currency').value,
        merchant: document.getElementById('merchant').value,
        transactionType: document.getElementById('transactionType').value
    };

    submitBtn.disabled = true;
    submitBtn.textContent = 'Submitting…';
    emptyState.style.display = 'none';
    stepsEl.style.display = 'flex';

    const startTime = Date.now();

    try {
        const res = await fetch(`${apiBase}/api/v1/transactions`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (!res.ok) {
            const body = await res.text();
            throw new Error(`API returned ${res.status}: ${body || 'no response body'}`);
        }

        const tx = await res.json();
        const id = tx.id;

        setStep('submitted', 'done');
        setDetail('submitted', `id: ${id}`);
        setStep('queued', 'active');
        setDetail('queued', 'event published to transactions.raw');

        let scoredShown = false;
        const pollDeadline = startTime + 20000;

        pollTimer = setInterval(async () => {
            try {
                if (Date.now() > pollDeadline) {
                    clearInterval(pollTimer);
                    showError('Timed out waiting for a verdict. Kafka may be stuck on an old message, or the ML service is not running on http://localhost:8000.');
                    submitBtn.disabled = false;
                    submitBtn.textContent = 'Submit transaction';
                    return;
                }

                const pollRes = await fetch(`${apiBase}/api/v1/transactions/${id}`);
                if (!pollRes.ok) throw new Error(`Poll failed: ${pollRes.status}`);
                const current = await pollRes.json();

                if (current.status === 'PENDING') {
                    setStep('queued', 'done');
                    setStep('scored', 'active');
                    if (!scoredShown) {
                        setDetail('scored', 'waiting on ML anomaly score…');
                    }
                    return;
                }

                // terminal state reached
                clearInterval(pollTimer);
                const elapsed = ((Date.now() - startTime) / 1000).toFixed(1);

                setStep('queued', 'done');
                setStep('scored', 'done');
                setDetail('scored', current.anomalyScore != null ? `score: ${current.anomalyScore}` : 'scoring complete');

                const isFlagged = current.status === 'FLAGGED';
                setStep('final', isFlagged ? 'flagged' : 'done');
                setDetail('final', current.status);

                document.getElementById('resultStatus').textContent = current.status;
                document.getElementById('resultStatus').className = 'result-status ' + (isFlagged ? 'flagged' : 'approved');
                document.getElementById('metaId').textContent = current.id;
                document.getElementById('metaScore').textContent = current.anomalyScore != null ? current.anomalyScore : 'n/a';
                document.getElementById('metaModel').textContent = current.modelVersion || 'n/a';
                document.getElementById('metaTime').textContent = elapsed + 's';
                resultCard.classList.add('show');

                submitBtn.disabled = false;
                submitBtn.textContent = 'Submit transaction';

            } catch (pollErr) {
                clearInterval(pollTimer);
                showError('Lost connection while polling for status: ' + pollErr.message);
                submitBtn.disabled = false;
                submitBtn.textContent = 'Submit transaction';
            }
        }, 1000);

    } catch (err) {
        submitBtn.disabled = false;
        submitBtn.textContent = 'Submit transaction';
        let hint = '';
        if (err.message === 'Failed to fetch') {
            hint = ' This usually means either the API isn\'t running, or the browser is blocking the request — check that CORS is enabled on the Spring Boot app for this page\'s origin.';
        }
        showError(err.message + hint);
    }
});