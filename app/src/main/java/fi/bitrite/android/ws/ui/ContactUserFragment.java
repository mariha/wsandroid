package fi.bitrite.android.ws.ui;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import fi.bitrite.android.ws.R;
import fi.bitrite.android.ws.WSAndroidApplication;
import fi.bitrite.android.ws.model.Host;
import fi.bitrite.android.ws.repository.MessageRepository;
import fi.bitrite.android.ws.ui.util.DialogHelper;
import fi.bitrite.android.ws.ui.util.NavigationController;
import fi.bitrite.android.ws.ui.util.ProgressDialog;
import fi.bitrite.android.ws.util.Tools;
import io.reactivex.android.schedulers.AndroidSchedulers;

/**
 * Responsible for letting the user type in a message and then sending it to an other user
 * over the Warmshowers web service.
 */
public class ContactUserFragment extends BaseFragment {

    private final static String KEY_RECIPIENT = "recipient";

    @Inject MessageRepository mMessageRepository;
    @Inject NavigationController mNavigationController;

    @BindView(R.id.contact_user_txt_subject) EditText mTxtSubject;
    @BindView(R.id.contact_user_txt_message) EditText mTxtMessage;
    @BindView(R.id.all_btn_submit) Button mBtnSubmit;
    @BindView(R.id.all_lbl_no_network_warning) TextView mLblNoNetworkWarning;

    private Host mRecipient;

    private ProgressDialog.Disposable mProgressDisposable;

    public static Fragment create(Host recipient) {
        Bundle bundle = new Bundle();
        bundle.putParcelable(KEY_RECIPIENT, recipient);

        Fragment fragment = new ContactUserFragment();
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_contact_user, container, false);
        ButterKnife.bind(this, view);

        mRecipient = getArguments().getParcelable(KEY_RECIPIENT);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        boolean isConnected = Tools.isNetworkConnected(getContext());
        mLblNoNetworkWarning.setVisibility(isConnected ? View.GONE : View.VISIBLE);
        mBtnSubmit.setEnabled(isConnected);
    }

    @OnClick(R.id.all_btn_submit)
    public void sendMessageToHost() {
        String subject = mTxtSubject.getText().toString();
        String message = mTxtMessage.getText().toString();

        if (TextUtils.isEmpty(subject) || TextUtils.isEmpty(message)) {
            DialogHelper.alert(getContext(), R.string.message_validation_error);
            return;
        }

        mProgressDisposable = ProgressDialog.create(R.string.sending_message)
                .show(getActivity());

        List<String> recipients = Collections.singletonList(mRecipient.getName());
        mMessageRepository.createThread(subject, message, recipients)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(threadId -> {
                    if (threadId == MessageRepository.STATUS_NEW_THREAD_ID_NOT_YET_KNOWN) {
                        // We wait for the threadId to become available.
                        return;
                    }

                    mProgressDisposable.dispose();

                    // The back action should take us from the message thread to the caller of this
                    // fragment and not this form fragment itself.
                    mNavigationController.popBackStack();

                    if (threadId != MessageRepository.STATUS_NEW_THREAD_ID_NOT_IDENTIFIABLE) {
                        // This is a valid threadId.
                        mNavigationController.navigateToMessageThread(threadId);
                    } else {
                        // We just navigate to the thread list...
                        mNavigationController.navigateToMessageThreads();
                    }
                }, throwable -> {
                    mProgressDisposable.dispose();

                    Toast.makeText(getContext(), R.string.message_thread_create_failed,
                            Toast.LENGTH_LONG).show();
                    Log.e(WSAndroidApplication.TAG,
                            "Failed to create a new message thread: " + throwable.getMessage());
                });
    }

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.title_fragment_contact_user, mRecipient.getFullname());
    }
}