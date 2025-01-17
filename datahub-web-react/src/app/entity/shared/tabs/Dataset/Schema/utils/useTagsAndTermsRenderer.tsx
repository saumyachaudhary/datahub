import React from 'react';
import {
    EditableSchemaFieldInfo,
    EditableSchemaMetadata,
    EntityType,
    GlobalTags,
    GlobalTagsUpdate,
    SchemaField,
} from '../../../../../../../types.generated';
import TagTermGroup from '../../../../../../shared/tags/TagTermGroup';
import { pathMatchesNewPath } from '../../../../../dataset/profile/schema/utils/utils';
import { useEntityData, useRefetch } from '../../../../EntityContext';

export default function useTagsAndTermsRenderer(
    editableSchemaMetadata: EditableSchemaMetadata | null | undefined,
    onUpdateTags: (update: GlobalTagsUpdate, record?: EditableSchemaFieldInfo) => Promise<any>,
    tagHoveredIndex: string | undefined,
    setTagHoveredIndex: (index: string | undefined) => void,
    options: { showTags: boolean; showTerms: boolean },
) {
    const { urn } = useEntityData();
    const refetch = useRefetch();

    const tagAndTermRender = (tags: GlobalTags, record: SchemaField, rowIndex: number | undefined) => {
        const relevantEditableFieldInfo = editableSchemaMetadata?.editableSchemaFieldInfo.find(
            (candidateEditableFieldInfo) => pathMatchesNewPath(candidateEditableFieldInfo.fieldPath, record.fieldPath),
        );

        return (
            <TagTermGroup
                uneditableTags={options.showTags ? tags : null}
                editableTags={options.showTags ? relevantEditableFieldInfo?.globalTags : null}
                uneditableGlossaryTerms={options.showTerms ? record.glossaryTerms : null}
                editableGlossaryTerms={options.showTerms ? relevantEditableFieldInfo?.glossaryTerms : null}
                canRemove
                buttonProps={{ size: 'small' }}
                canAddTag={tagHoveredIndex === `${record.fieldPath}-${rowIndex}` && options.showTags}
                canAddTerm={tagHoveredIndex === `${record.fieldPath}-${rowIndex}` && options.showTerms}
                onOpenModal={() => setTagHoveredIndex(undefined)}
                entityUrn={urn}
                entityType={EntityType.Dataset}
                entitySubresource={record.fieldPath}
                refetch={refetch}
            />
        );
    };
    return tagAndTermRender;
}
