# 逻辑模块: useCrudPage

## 模块描述
Load list

## 函数 (Functions)
### `isSuccess`
- **描述**: 无描述
- **签名**: `function isSuccess(result: CrudResult): boolean {
	if (typeof result === 'boolean') return result;
	return result.success;
}`

### `useCrudPage`
- **描述**: 无描述
- **签名**: `export function useCrudPage<T extends { id?: number }, TCreate = T, TUpdate = T>(
	options: UseCrudPageOptions<T, TCreate, TUpdate>,
): UseCrudPageReturn<T, TCreate, TUpdate> {
	const { loadFn, createFn, updateFn, deleteFn, defaultFormFactory } = options;

	const loading = ref(false);
	const saveLoading = ref(false);
	const items = ref<T[]>([]) as Ref<T[]>;
	const dialogVisible = ref(false);
	const isEdit = ref(false);
	const formRef = ref<{ validate: () => Promise<{ valid: boolean }> } | null>(null);
	const formData = ref<T>(defaultFormFactory()) as Ref<T>;

	async function loadItems() {
		loading.value = true;
		try {
			items.value = await loadFn();
		} finally {
			loading.value = false;
		}
	}

	function resetForm() {
		formData.value = defaultFormFactory();
	}

	function openCreateDialog() {
		isEdit.value = false;
		resetForm();
		dialogVisible.value = true;
	}

	function openEditDialog(item: T & { id?: number }) {
		isEdit.value = true;
		formData.value = { ...item };
		dialogVisible.value = true;
	}

	function closeDialog() {
		dialogVisible.value = false;
		resetForm();
	}

	async function saveItem(
		createData: TCreate | undefined,
		updateData: TUpdate | undefined,
		editId: number | null | undefined,
	): Promise<boolean> {
		const validated = await formRef.value?.validate();
		if (!validated?.valid) return false;

		saveLoading.value = true;
		try {
			if (isEdit.value && editId && updateFn && updateData !== undefined) {
				const result = await updateFn(editId, updateData);
				if (!isSuccess(result)) return false;
			} else if (!isEdit.value && createFn && createData !== undefined) {
				const result = await createFn(createData);
				if (!isSuccess(result)) return false;
			}
			dialogVisible.value = false;
			await loadItems();
			return true;
		} finally {
			saveLoading.value = false;
		}
	}

	async function deleteItem(id: number): Promise<boolean> {
		if (!deleteFn) return false;
		const result = await deleteFn(id);
		if (isSuccess(result)) {
			await loadItems();
			return true;
		}
		return false;
	}

	return {
		loading,
		saveLoading,
		items,
		dialogVisible,
		isEdit,
		formRef,
		formData,
		loadItems,
		openCreateDialog,
		openEditDialog,
		closeDialog,
		resetForm,
		saveItem,
		deleteItem,
	};
}`

## 类型定义 (Interfaces)
### `UseCrudPageOptions`
```typescript
export interface UseCrudPageOptions<T, TCreate = T, TUpdate = T> {
	/** Load list */
	loadFn: () => Promise<T[]>;
	/** Create (optional) */
	createFn?: (data: TCreate) => Promise<CrudResult>;
	/** Update (optional) */
	updateFn?: (id: number, data: TUpdate) => Promise<CrudResult>;
	/** Delete (optional) */
	deleteFn?: (id: number) => Promise<CrudResult>;
	/** Factory for empty form data */
	defaultFormFactory: () => T;
}
```

### `UseCrudPageReturn`
```typescript
export interface UseCrudPageReturn<T, TCreate = T, TUpdate = T> {
	loading: Ref<boolean>;
	saveLoading: Ref<boolean>;
	items: Ref<T[]>;
	dialogVisible: Ref<boolean>;
	isEdit: Ref<boolean>;
	formRef: Ref<{ validate: () => Promise<{ valid: boolean }> } | null>;
	formData: Ref<T>;
	/** Load/reload the list */
	loadItems: () => Promise<void>;
	/** Open the dialog in create mode */
	openCreateDialog: () => void;
	/** Open the dialog in edit mode, prefilling formData */
	openEditDialog: (item: T & { id?: number }) => void;
	/** Close and reset the dialog */
	closeDialog: () => void;
	/** Reset formData to default values */
	resetForm: () => void;
	/**
	 * Validate the form and call create or update.
	 * Returns true if save succeeded, false otherwise.
	 */
	saveItem: (
		createData: TCreate | undefined,
		updateData: TUpdate | undefined,
		editId: number | null | undefined,
	) => Promise<boolean>;
	/**
	 * Call deleteFn, reload on success.
	 * Consumer should handle the confirm dialog before calling this.
	 */
	deleteItem: (id: number) => Promise<boolean>;
}
```


---
> 🤖 AI 提示: 逻辑实现请参考 `useCrudPage/index.ts`。