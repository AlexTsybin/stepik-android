package org.stepic.droid.core.presenters.contracts

import org.stepik.android.model.user.User

interface InstructorsView {

    fun onLoadingInstructors()

    fun onFailLoadInstructors()

    fun onInstructorsLoaded(users: List<User>)

    fun onHideInstructors()

}
