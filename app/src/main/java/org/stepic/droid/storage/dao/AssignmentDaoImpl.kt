package org.stepic.droid.storage.dao

import android.content.ContentValues
import android.database.Cursor

import org.stepic.droid.jsonHelpers.adapters.UTCDateAdapter
import org.stepic.droid.storage.operations.DatabaseOperations
import org.stepic.droid.storage.structure.DbStructureAssignment
import org.stepik.android.model.Assignment

import javax.inject.Inject

class AssignmentDaoImpl
@Inject
constructor(
        databaseOperations: DatabaseOperations,
        private val dateAdapter: UTCDateAdapter
) : DaoBase<Assignment>(databaseOperations) {

    public override fun parsePersistentObject(cursor: Cursor): Assignment {
        val columnIndexAssignmentId = cursor.getColumnIndex(DbStructureAssignment.Column.ASSIGNMENT_ID)
        val columnIndexCreateDate = cursor.getColumnIndex(DbStructureAssignment.Column.CREATE_DATE)
        val columnIndexProgress = cursor.getColumnIndex(DbStructureAssignment.Column.PROGRESS)
        val columnIndexStepId = cursor.getColumnIndex(DbStructureAssignment.Column.STEP_ID)
        val columnIndexUnitId = cursor.getColumnIndex(DbStructureAssignment.Column.UNIT_ID)
        val columnIndexUpdateDate = cursor.getColumnIndex(DbStructureAssignment.Column.UPDATE_DATE)

        return Assignment(
                cursor.getLong(columnIndexAssignmentId),
                cursor.getLong(columnIndexStepId),
                cursor.getLong(columnIndexUnitId),
                cursor.getString(columnIndexProgress),

                dateAdapter.stringToDate(cursor.getString(columnIndexCreateDate)),
                dateAdapter.stringToDate(cursor.getString(columnIndexUpdateDate))
        )
    }

    public override fun getDbName(): String = DbStructureAssignment.ASSIGNMENTS

    public override fun getContentValues(assignment: Assignment): ContentValues {
        val values = ContentValues()

        values.put(DbStructureAssignment.Column.ASSIGNMENT_ID, assignment.id)
        values.put(DbStructureAssignment.Column.PROGRESS, assignment.progress)
        values.put(DbStructureAssignment.Column.STEP_ID, assignment.step)
        values.put(DbStructureAssignment.Column.UNIT_ID, assignment.unit)
        values.put(DbStructureAssignment.Column.CREATE_DATE, dateAdapter.dateToString(assignment.createDate))
        values.put(DbStructureAssignment.Column.UPDATE_DATE, dateAdapter.dateToString(assignment.updateDate))

        return values
    }

    public override fun getDefaultPrimaryColumn(): String =
            DbStructureAssignment.Column.ASSIGNMENT_ID

    public override fun getDefaultPrimaryValue(persistentObject: Assignment): String =
            persistentObject.id.toString()
}
