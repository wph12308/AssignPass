package com.example.assignpass

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.assignpass.ui.AddEditAssignmentScreen
import com.example.assignpass.ui.AssignmentListScreen
import com.example.assignpass.ui.AssignmentViewModel
import com.example.assignpass.ui.theme.AssignPassTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AssignPassTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: AssignmentViewModel = viewModel()
                    val isShowingForm by viewModel::isShowingForm

                    // 系统返回键：在表单页面则返回列表，否则退出应用
                    BackHandler(enabled = isShowingForm) {
                        viewModel.cancelForm()
                    }

                    if (isShowingForm) {
                        AddEditAssignmentScreen(
                            viewModel = viewModel,
                            onNavigateBack = { viewModel.cancelForm() }
                        )
                    } else {
                        AssignmentListScreen(
                            viewModel = viewModel,
                            onAddAssignment = { viewModel.startAddAssignment() },
                            onEditAssignment = { viewModel.startEditAssignment(it) }
                        )
                    }
                }
            }
        }
    }
}
