[中文](./KNOWLEDGE_USAGE.md) | English

---

# DataAgent Knowledge Configuration Best Practices Guide

In the DataAgent system, the AI's capability ceiling depends on the quality of "knowledge" it possesses. To enable the agent to accurately understand user natural language queries and convert them into correct SQL and analysis reports, we need to configure three types of knowledge in layers: **Semantic Model**, **Business Knowledge**, and **Agent Knowledge Base**.

---

## I. Semantic Model Configuration

**Positioning**: This is the **foundation** of NL2SQL. It is responsible for establishing hard mappings from "human language" to "database physical tables/fields". If this is configured incorrectly, the SQL will definitely be wrong.

### 1. Core Field Configuration Tips
The configuration focus is on eliminating ambiguity in database fields. The semantic model doesn't require you to configure all tables and columns here - there are too many tables to configure completely anyway. If you find that the description field of a data column has too little information for the LLM to understand well, or a very "professional" business term maps to a certain column that the LLM can't understand, you can try configuring it here.

*   **Table Name/Database Field Name**:
    *   Keep consistent with the database physical name.
*   **Business Name**:
    *   **Best Practice**: Use standard names commonly used in business people's spoken language.
    *   *Bad Example*: `csat_score` -> "CSAT" (too technical)
    *   *Good Example*: `csat_score` -> "Customer Satisfaction Score"
*   **Synonyms -- Most Important Configuration**:
    *   **Purpose**: Solves the problem of diverse user phrasing.
    *   **Best Practice**: Enumerate all possible names, separated by commas.
    *   *Examples*:
        *   Field `price2` (order price): Synonyms should include `order amount, transaction price, sales amount, payment amount, amount`
        *   Field `user_id`: Synonyms should include `user ID, member ID, customer number`
*   **Business Description -- Resolves Logical Ambiguity**:
    *   **Purpose**: Tells the LLM the special value logic or business meaning of this field.
    *   **Best Practice**: If it's an enumeration or status code, **must** explain the meaning of each value.
    *   *Example*: For the `order_status` field, the description should be: `Order status: 0 represents pending payment, 1 represents paid/completed, 2 represents cancelled, 3 represents refunding. When calculating GMV, usually only orders with status 1 are counted.`
*   **Data Type**:
    *   Ensure accuracy (such as `int`, `varchar`), this determines whether the generated SQL adds quotes to values.

---

## II. Business Knowledge Configuration

**Positioning**: This is DataAgent's **business logic dictionary**. It solves "what is...?" questions, used to define calculation formulas, proprietary terms, and business metrics. Note that the **business name in the semantic model** focuses on mapping to tables, but not all business terms will have a one-to-one correspondence with tables.

### 1. Applicable Scenarios
When users ask "What was last month's **GMV**?" or "What is the **high-value customer** ratio?", the LLM doesn't know the GMV formula nor the definition of high-value customers. This is when business knowledge recall (Recall) is needed.

### 2. Configuration Best Practices
*   **Business Name**: Fill in the standard name of the metric or term.
    *   *Examples*: `GMV`, `Repurchase Rate`, `High-Potential Customer`.
*   **Description -- Core Logic Area**:
    *   **Best Practice**: Clearly describe calculation formulas, filter conditions, or business definitions in natural language.
    *   *GMV Example*: `GMV (Gross Merchandise Volume) = Total order amount of all orders with status 'paid' or 'shipped' in the order table, without deducting refund amount.`
    *   *Repurchase Rate Example*: `Repurchase Rate = Number of deduplicated users with purchase count >= 2 in the selected time period / Total deduplicated purchase users.`
*   **Synonyms**:
    *   *Example*: Synonyms for `GMV`: `transaction volume, trading volume, total site sales`
*   **Enable Recall**:
    *   Must be enabled. When enabled, when users' questions involve these terms, the system will inject this "description" as context to the prompt through vector retrieval, guiding the SQL generation node to write the correct formula.

---

## III. Agent Knowledge Base Configuration

**Positioning**: This is DataAgent's **external brain expansion** (RAG). Supports unstructured document uploads to provide background information, industry knowledge, SOPs, or historical cases.

### 1. Knowledge Types and Uses
Supports `Documents`, `Q&A Pairs`, etc.

#### A. Documents (File Upload: PDF, DOCX, MD)
*   **Uses**:
    1.  **Database Schema Manual**: If the table structure is extremely complex and the semantic model can't fit everything, you can upload a detailed database design document.
    2.  **Business Process SOP**: Helps the Planner (planning node) understand business processes, and even better, follow the data analysis steps you want. For example, if you find the Agent's "sales forecast" is always not done well, and your company has its own sales forecast process with specific steps, you can write "Steps for sales forecasting" in the document, directly specifying the sales forecast process. **Strongly recommend using this feature**. Of course, you can also put it in Q&A pairs, or put multiple Q&A pairs into one document and upload it.
    3.  **Industry Reports**: Helps the Report Generator (report generation node) add industry insights and background knowledge when generating the final HTML report, rather than just listing data.

#### B. Q&A Pairs/FAQ -- Tuning Tool
This is the most efficient way to correct Agent's incorrect behavior (Few-Shot Learning).

*   **Use**: When you find the Agent's SQL generation is always wrong for certain types of questions, don't keep modifying the Prompt, just add a Q&A.
*   **Best Practice Configuration**:
    *   **Question (Q)**: Simulate user's question. Example: `Calculate last month's sales`
    *   **Answer (A)**: Provide standard SQL writing or Chain of Thought (CoT).
    *   *Example*:
        *   **Q**: `Query last year's active user count`
        *   **A**: `Active users are defined as users with login count > 3 times. SQL logic should be: SELECT count(distinct user_id) FROM login_logs WHERE login_time >= '2023-01-01' AND login_time <= '2023-12-31' GROUP BY user_id HAVING count(*) > 3`.

---

## IV. Knowledge Workflow Integration

In a complete DataAgent run, how do these three layers of knowledge collaborate?

**Scenario**: User asks **"Please analyze the GMV trend in the East China region last month and generate a report."**

- **Evidence Recall Phase (EvidenceNode)**:
  *   System retrieves **"Business Knowledge"** and finds the definition of `GMV` (total amount of paid orders).
  *   System retrieves **"Agent Knowledge Base"** and may find a document about "Regional Division Standards", knowing which provinces "East China" includes.
- **Query Enhancement Phase (QueryEnhanceNode)**:
  - Used to translate business based on evidence information and rewrite the user's query.

- **SchemaRecallNode & TableRelationNode**:
  - Recall tables and columns using the rewritten query above
  - TableRelationNode uses the large model to select tables related to the question, and find missing tables through foreign keys, ultimately getting data tables and columns (schema) related to the question
  - TableRelationNode recalls corresponding semantic models based on these tables.


- **PlannerNode**:
  - Combining the above evidence with the database schema and semantic model, the LLM has comprehensive business and database information to make a relatively complete and correct plan.

- **SQL Generation Phase (SQLGenerateNode)**:
  *   Using **"Semantic Model"**, maps "order" to `orders` table, "amount" to `price2` field, "region" to `region` field.
  *   Combined with the `GMV` definition, generates `WHERE status = 1` filter condition.
  *   Generates SQL: `SELECT date, SUM(price2) FROM orders WHERE region IN ('Shanghai','Jiangsu'...) AND status=1 ...`
- **Report Generation Phase (Report Generator Node)**:
  *   LLM receives SQL execution data results.
  *   LLM combines industry background knowledge from **"Agent Knowledge Base"** to interpret GMV trends (for example: combined with "peak/off-season explanation" in the knowledge base, analyze why data rises or falls).
  *   Finally outputs HTML report/MD format report.

---

## V. Pitfall Avoidance Guide (Checklist)

1.  **Don't write complex calculation logic in the semantic model**: The semantic model is only responsible for field mapping. Complex formulas (like A/B*C) should be defined in "Business Knowledge".
2.  **Don't abuse synonyms**: Don't fill in unrelated words, otherwise it can easily cause incorrect knowledge recall.
3.  **Q&A is the fire brigade**: If the Agent can't learn a certain SQL writing style no matter how you teach it, uploading a Q&A containing that SQL example is the fastest solution.
4.  **Descriptions should be in plain language**: When filling in "Business Description", treat the LLM as a new intern and explain field meanings in the most straightforward language, avoiding internal jargon (unless you've defined the jargon in business knowledge).
