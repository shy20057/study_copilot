### Task 6: Task Core, Hierarchy, Ordering, and Optimistic Versions

> 历史归档：本文记录早期方案或实施过程，其中的流程要求、授权和验证结论仅适用于当时。当前状态见 [后端交付说明](../../../04-交付验收/一期A后端交付说明.md)，旧路径的新位置见 [归档索引](../../README.md)。

**Files:**
- Modify: `backend/src/main/java/com/example/studycopilot/dao/entity/task/TaskDO.java`
- Modify: `backend/src/main/java/com/example/studycopilot/dto/req/task/**`
- Modify: `backend/src/main/java/com/example/studycopilot/dto/resp/task/TaskResponse.java`
- Modify: `backend/src/main/java/com/example/studycopilot/controller/task/TaskController.java`
- Modify: `backend/src/main/java/com/example/studycopilot/service/task/**`
- Create: `backend/src/main/java/com/example/studycopilot/domain/task/TaskPolicy.java`
- Create: `backend/src/main/java/com/example/studycopilot/dto/req/task/ReorderTasksRequest.java`
- Create: `backend/src/test/java/com/example/studycopilot/api/TaskApiTest.java`

**Interfaces:**
- Produces: task list filters `ALL`, `TODO`, `DONE`, `CANCELLED` with complete counts independent of dashboard truncation.
- Produces: versioned mutations whose request includes `expectedVersion`.

- [ ] **Step 1: Write task tests**

Test create, IME-safe API semantics, ownership, parent/child completion, reopening a child, cancel/restore, soft-delete restore window metadata, reorder conflicts, top-level completion rates, and session-linked task restrictions.

```java
@Test
void reopeningChildReopensCompletedParent() {
    TaskAggregate aggregate = completedParentWithTwoChildren();
    aggregate.reopenChild(CHILD_ONE);
    assertThat(aggregate.parent().status()).isEqualTo(TaskStatus.TODO);
}
```

- [ ] **Step 2: Run tests and confirm failure**

```powershell
mvn -f backend/pom.xml -Dspring.profiles.active=test -Dtest=TaskApiTest test
```

- [ ] **Step 3: Implement task policy and persistence**

Allow one child level only. Completing a parent with incomplete children requires `completeChildren=true`; otherwise return the incomplete count. Completing all non-cancelled children completes the parent. Cancelling all children does not auto-complete the parent.

- [ ] **Step 4: Implement ordering and version conflicts**

Reorder accepts the complete ordered id list for the current filter plus each expected version. Apply in one transaction or return `VERSION_CONFLICT` without a partial reorder.

- [ ] **Step 5: Run task tests**

Run Step 2; expect PASS.

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/java/com/example/studycopilot/domain/task backend/src/main/java/com/example/studycopilot/controller/task backend/src/main/java/com/example/studycopilot/service/task backend/src/main/java/com/example/studycopilot/dao/entity/task backend/src/main/java/com/example/studycopilot/dto/req/task backend/src/main/java/com/example/studycopilot/dto/resp/task backend/src/test/java/com/example/studycopilot/api/TaskApiTest.java
git commit -m "feat: implement versioned task workflows"
```

---

