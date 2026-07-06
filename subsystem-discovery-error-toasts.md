# Subsystem Discovery Feature: Frontend Toast Messages & Error Catalog

This catalog outlines all possible error scenarios that can occur during the execution of Subsystem Discovery APIs (`discover-llm`, `summary`, and `boundary/analyze`), mapping backend exception codes and message patterns to user-friendly frontend toast notifications.

---

## 1. Backend Exception Handling Architecture

The backend uses a global exception handler (`ApiExceptionHandler`) that catches exceptions and maps them to a structured JSON response.

### Successful Error Response Schema (4xx/5xx):
```json
{
  "timestamp": "2026-07-02T15:15:30.123Z",
  "status": 400,
  "error": "IllegalArgumentException",
  "message": "Detailed explanation of what went wrong"
}
```

* **`status`**: HTTP Status (e.g., `400` or `500`)
* **`error`**: Simple Java exception class name (e.g., `IllegalArgumentException`, `IllegalStateException`)
* **`message`**: The descriptive error message thrown by the backend business logic.

---

## 2. Master Error Catalog Table

The table below covers all known errors originating from backend validations, database constraints, external LLM calls, and network protocols.

| Endpoint | HTTP Status | Exception Class / Error Code | Backend Message Pattern | Proposed Frontend Toast Message | Toast Severity |
| :--- | :---: | :--- | :--- | :--- | :---: |
| **`/discover-llm`** | `400` | `IllegalArgumentException` | `Cannot resolve application metadata for analysisTime=...` | **"Unable to start discovery: A valid snapshot analysis time is required."** | 🔴 Error |
| **`/discover-llm`** | `400` | `IllegalStateException` | `Cannot discover subsystems from an empty graph` | **"Discovery failed: The selected graph contains no nodes or edges to analyze."** | 🔴 Error |
| **`/discover-llm`** | `400` | `IllegalStateException` | `Failed to retrieve master run after execution` | **"Discovery completed, but system could not retrieve the run metadata. Please refresh."** | 🟡 Warning |
| **`/discover-llm`** | `400` | `IllegalStateException` | `Failed to deserialize discovery result for run ID: ...` | **"Unable to parse subsystem details due to data format errors."** | 🔴 Error |
| **`/discover-llm`** | `400` | `IllegalStateException` | `Failed to persist discovery run results to the database` | **"Discovery completed, but saving the results to the database failed."** | 🔴 Error |
| **`/discover-llm`** | `200` *(Fallback)* | *Graceful catch inside service* | `LLM architectural summary not available...` | **"Discovery successful, but AI summary could not be generated. Default labels used."** | 🟡 Warning |
| **`/discover-llm`** | `400` | `IllegalStateException` | `Failed to persist LLM architectural summary to the database` | **"Discovery run saved, but failed to cache the AI summary in the database."** | 🟡 Warning |
| **`/summary`** | `400` | `IllegalArgumentException` | `No discovery run found for ID: ...` | **"Failed to load summary: The specified discovery run ID does not exist."** | 🔴 Error |
| **`/summary`** | `400` | `IllegalStateException` | `No discovery result data found for run ID: ...` | **"Failed to generate summary: Subsystem data is missing for this run."** | 🔴 Error |
| **`/summary`** | `400` | `IllegalStateException` | `Failed to deserialize discovery result for run ID: ...` | **"Failed to generate summary: Subsystem details are corrupted or unreadable."** | 🔴 Error |
| **`/summary`** | `400` | `IllegalStateException` | `Failed to persist LLM architectural summary to the database` | **"Summary generated successfully, but it could not be saved to the database."** | 🟡 Warning |
| **`/boundary/analyze`** | `400` | `IllegalArgumentException` | `Either discoveryRunId or analysisTime must be provided.` | **"Boundary analysis failed: Missing target discovery run ID or analysis time."** | 🔴 Error |
| **`/boundary/analyze`** | `400` | `IllegalArgumentException` | `No discovery run found for discoveryRunId=...` | **"Boundary analysis failed: The selected discovery run does not exist."** | 🔴 Error |
| **`/boundary/analyze`** | `400` | `IllegalArgumentException` | `Cannot resolve application metadata for analysisTime=...` | **"Boundary analysis failed: Invalid or missing snapshot analysis time."** | 🔴 Error |
| **`/boundary/analyze`** | `400` | `IllegalStateException` | `No discovery result data found for discoveryRunId=...` | **"Boundary analysis failed: No discovery result data was found for this run."** | 🔴 Error |
| **`/boundary/analyze`** | `400` | `IllegalStateException` | `Failed to deserialise discovery result for discoveryRunId=...` | **"Boundary analysis failed: Subsystem details are corrupted or unreadable."** | 🔴 Error |
| **`/boundary/analyze`** | `400` | `IllegalStateException` | `Node assignments are not available for discoveryRunId=...` | **"Boundary node data is missing. Please re-run Leiden discovery to regenerate the run."** | 🔴 Error |
| **`/boundary/analyze`** | `400` | `IllegalStateException` | `Node assignments were unexpectedly empty after a fresh discovery run.` | **"Boundary node analysis failed: Leiden algorithm returned no node assignments."** | 🔴 Error |
| **Any / General** | `0` / Client | `Network Error` | *Service connection failed / Timed out* | **"Connection failed: Unable to reach the server. Please check your connection."** | 🔴 Error *(with Retry)* |
| **Any / General** | `404` | `Not Found` | *URL context or resource path incorrect* | **"API endpoint not found. Please verify backend service configuration."** | 🔴 Error |
| **Any / General** | `500` | `InternalServerError` | *Any unhandled Exception or DB connection failure* | **"Internal Server Error: An unexpected error occurred on the server."** | 🔴 Error |
| **Any / General** | `503` | `ServiceUnavailable` | *Server overloaded or undergoing maintenance* | **"Service Unavailable: The server is temporarily busy. Please try again soon."** | 🔴 Error |

---

## 3. Detailed Scenario Breakdowns

### A. Subsystem Discovery with LLM (`/discover-llm`)

1. **Invalid or Missing Snapshot Time (`analysisTime`)**
   * **Cause**: Frontend sends a request without the `analysisTime` query parameter, or uses a string that doesn't exist in `tb_node_history.analysis_time`.
   * **Toast Detail**: *"Unable to start discovery: A valid snapshot analysis time is required."*
   * **Toast Severity**: `Error` (🔴 Red)
   * **Suggested User Action**: Choose a different snapshot time from the selector and try again.

2. **Graph Structure is Empty**
   * **Cause**: The snapshot exists in history metadata, but has no actual node or edge records in dependency history tables.
   * **Toast Detail**: *"Discovery failed: The selected graph contains no nodes or edges to analyze."*
   * **Toast Severity**: `Error` (🔴 Red)
   * **Suggested User Action**: Re-import or select another dataset with code entities.

3. **External LLM Service is Down or Unconfigured**
   * **Cause**: Backend configuration has `subsystem.llm.enabled=false`, API key is missing, or the LLM provider (DeepSeek, Gemini, Llama) fails/times out.
   * **Toast Detail**: *"Discovery successful, but AI summary could not be generated. Default labels used."*
   * **Toast Severity**: `Warning` (🟡 Yellow)
   * **Technical Context**: The backend handles this gracefully. It returns status `200` but puts a default fallback sentence into the `summary` text. This message is a warning to the user that heuristics were used instead of LLM tags, but the visual Leiden graph is still rendered.

---

### B. Summary Generation (`/summary`)

1. **Target Discovery Run ID Missing or Deleted**
   * **Cause**: Request triggers summary generation for a `discoveryRunId` that has been deleted or does not match any entry in the DB.
   * **Toast Detail**: *"Failed to load summary: The specified discovery run ID does not exist."*
   * **Toast Severity**: `Error` (🔴 Red)
   * **Suggested User Action**: Refresh the dashboard to sync the active run IDs list.

2. **Database Save Failures**
   * **Cause**: LLM summary generated successfully, but the database connection was dropped before caching it.
   * **Toast Detail**: *"Summary generated successfully, but it could not be saved to the database."*
   * **Toast Severity**: `Warning` (🟡 Yellow)
   * **Technical Context**: The generated summary is returned to the user, but subsequent requests will hit the LLM again instead of using the cache.

---

### C. Boundary Node Detection (`/boundary/analyze`)

1. **Legacy Discovery Run Format (Pre-Boundary Support)**
   * **Cause**: The database contains cached discovery records created with an older version of the Leiden tool that did not persist node-to-cluster mappings (`node_assignments`).
   * **Toast Detail**: *"Boundary node data is missing. Please re-run Leiden discovery to regenerate the run."*
   * **Toast Severity**: `Error` (🔴 Red)
   * **Suggested User Action**: Directs the user to re-run the discovery step for this dataset config, forcing a fresh save containing node assignments.

---

## 4. Toast Design & UX Guidelines

To ensure a high-quality frontend experience, implement the following toast parameters:

* **Auto-Dismiss Timer**:
  * **Success/Info Toasts**: Dismiss automatically after **4 seconds** (e.g., *"Summary successfully generated"*).
  * **Warning Toasts**: Dismiss after **6 seconds** (e.g., *"LLM summary generation failed - using heuristics"*).
  * **Error Toasts**: Keep open until closed manually (provide `[×]` button) or dismiss after **8 seconds** to give users time to read.
* **Colors & Icons**:
  * **Success**: Green fill/border, checkmark icon (`✓`)
  * **Warning**: Orange/Yellow fill/border, warning icon (`⚠`)
  * **Error**: Red fill/border, exclamation/alert icon (`🚫` or `⚠`)
* **Action Triggers**:
  * For **Network Errors**, include a **"Retry"** text button directly in the toast to re-trigger the API call.

---

## 5. Critical Recommendation: Fix the Frontend `handleError` Helper

During analysis of `poc-ui/src/App.vue`, we observed that the helper function `handleError` displays the generic class name of the exception rather than the descriptive backend message:

### Current Implementation (`App.vue` Line 767-770):
```javascript
function handleError(error, fallback) {
  // Extracts the java class name (e.g., "IllegalStateException")
  const serverError = error?.response?.data?.error
  errorMessage.value = serverError ? `${fallback}: ${serverError}` : `${fallback}: ${error.message}`
}
```

### Problem:
Because the backend puts `ex.getClass().getSimpleName()` in the `"error"` field and `ex.getMessage()` in the `"message"` field, this code results in toasts like:
* *❌ "Subsystem discovery failed: IllegalStateException"*
* *❌ "Failed to generate graph data: IllegalArgumentException"*

### Solution:
Update `handleError` in `App.vue` to check for `response.data.message` first:

```javascript
function handleError(error, fallback) {
  const data = error?.response?.data
  // Fall back to message, then error class name, then client-side error message
  const serverMessage = data?.message || data?.error || error.message
  errorMessage.value = serverMessage ? `${fallback}: ${serverMessage}` : fallback
}
```
Applying this fix ensures that the toasts display the exact diagnostic messages (e.g. *"Subsystem discovery failed: Cannot discover subsystems from an empty graph"*) instead of raw Java class names.
