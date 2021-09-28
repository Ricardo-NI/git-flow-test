package ezpay.atile.ezpay;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import ezpay.atile.ezpay.AsyncTasks.AsyncInterface;
import ezpay.atile.ezpay.AsyncTasks.GetOrdersTask;
import ezpay.atile.ezpay.Class.ManagerBiometryStatus;
import ezpay.atile.ezpay.Class.OrderRecyclerAdapter;
import ezpay.atile.ezpay.Class.SetErrorMessageFail;
import ezpay.atile.ezpay.Class.SoundManager;
import ezpay.atile.ezpay.Class.Util;
import ezpay.atile.ezpay.ClassController.NSUTableController;
import ezpay.atile.ezpay.ClassController.OrderController;
import ezpay.atile.ezpay.ClassModel.Config;
import ezpay.atile.ezpay.ClassModel.Order;
import ezpay.atile.ezpay.ClassModel.StatusRecord;
import ezpay.atile.ezpay.ClassReturns.Order.ReturnOrder;
import ezpay.atile.ezpay.ClassReturns.nsu;

/**
 * Created by robsonsilva on 12/26/17.
 */

public class OrderHistory extends Fragment implements FragmentCallback {


    public static Order order = null;
    private List<nsu> nsuList;
    FragmentActivity listener;
    FragmentCallback fragmentCallback;
    private boolean nsuContains;
    private RecyclerView OrderListView = null;
    Context context;
    private String tableIdPush = null;
    private List<Order> OrderList = null;
    private RequestServerTask requestServerTask;

    ConstraintLayout constraintLayout;
    private SwipeRefreshLayout refreshLayout;

    @Override
    public void onAttach(Context context) {

        this.context = context;
        super.onAttach(context);

        if (context instanceof Activity) {
            this.listener = (FragmentActivity) context;
        }
    }

    private boolean isConnectedInternet() {

        return ConnectivityReceiver.isConnected(getContext());
    }

    @Override
    public void onStart() {

        progressBarManager(true);

        requestServerTask = new RequestServerTask();
        requestServerTask.execute(false);

        // populateListView2(false);//para testes

        LocalBroadcastManager.getInstance(getContext()).registerReceiver((mMessageReceiver), new IntentFilter("MyData"));

        super.onStart();
    }

    @Override
    public void onAttach(String FragmentName) {
        // mCallback.onAttach(FragmentName);
    }

    public FragmentCallback mCallback;

    public void setListener(FragmentCallback listener) {
        mCallback = listener;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.orderhistory2, parent, false);
        OrderListView = view.findViewById(R.id.lstOrderHistory);
        LinearLayoutManager mLayoutManager = new LinearLayoutManager(getContext());
        OrderListView.setLayoutManager(mLayoutManager);
        OrderListView.setHasFixedSize(true);
        //OrderRecycleAdapter adapter = null;
        //OrderListView.setAdapter(adapter);

        int biometryStatus = ManagerBiometryStatus.GetBiometryStatus();
        if(biometryStatus != 0) { //processo de biometria não iniciado ou não finalizado.
            constraintLayout = view.findViewById(R.id.constraintLayout);
            constraintLayout.setVisibility(View.VISIBLE);
            TextView textView = view.findViewById(R.id.textView69);

            /*status da validação biométrica:
          0 - finalizado ou com erros na busca do status.
          1 - em processamento.
          2 - recadastrar nome, sobrenome e cpf.
          3 - recadastrar imagem do documento.
          4 - recadastrar imagens das selfies.
          5 - começar pelo início do processo de validação.
          */

            switch (biometryStatus){
                case 1://exibir mensagem de que os dados estão em análise, caso alguma imagem ainda esteja em processamento.
                    TextView textView1 = view.findViewById(R.id.textView72);
                    TextView textView2 = view.findViewById(R.id.textView73);
                    textView1.setVisibility(View.GONE);
                    textView2.setVisibility(View.GONE);
                    textView.setText(getString(R.string.text_biostatus_05));
                    break;
                case 2://requisitar ao usuário para informar seus dados novamente caso não tenham sido validados.
                    goToAlterData();
                    break;
                case 3://requisitar a foto do documento caso o processo não tenha sido iniciado ou validado.
                    goToBiometry(2);
                    break;
                case 4://requisitar as selfies caso o processo de alguma não tenha sido iniciado ou validado.
                    goToBiometry(3);
                    break;
                case 5://iniciar processo de biometria.
                    goToBiometry(1);

            }
            if(biometryStatus > 1 && biometryStatus < 5){
                textView.setText(getString(R.string.text_biostatus_04));
            }
        }

        refreshLayout = view.findViewById(R.id.refreshLayout);
        refreshLayout.setOnRefreshListener(() -> {
            if(isConnectedInternet()){
                refreshOrders();
            }else {
                refreshLayout.setRefreshing(false);
                ConexInternetDialog newFragment = new ConexInternetDialog();
                newFragment.show(getFragmentManager(),"no_conex");
            }
        });

        return view;
    }

    private void goToBiometry(int pos){

        constraintLayout.setOnClickListener(view1 -> {
            Intent intent = null;

            switch (pos){
                case 1:
                    intent = new Intent(getActivity(), BiometricValidationActivity.class);
                    break;
                case 2:
                    intent = new Intent(getActivity(), BiometricIdentityActivity.class);
                    intent.putExtra("onlyDocument", ManagerBiometryStatus.SelfiesAreUploaded());
                    break;
                case 3:
                    intent = new Intent(getActivity(), BiometricProofOfLifeActivity.class);
                    break;
            }

            //para testes
            //intent = new Intent(getActivity(), BiometricUserActivity.class);

            assert intent != null;
            intent.putExtra("isRegister", false);
            startActivity(intent);

            if(getActivity() != null)
            getActivity().finish();
        });

    }

    private void goToAlterData(){

        constraintLayout.setOnClickListener(view1 -> {

            FragmentManager fragmentManager = getFragmentManager();
            if(fragmentManager != null) {
                FragmentTransaction ft = fragmentManager.beginTransaction();
                ft.setCustomAnimations(R.anim.enter_from_left, R.anim.exit_to_right, R.anim.enter_from_right, R.anim.exit_to_left);

                BiometricFragmentAlterData alterData = new BiometricFragmentAlterData();
                alterData.setListener(OrderHistory.this);

                ft.replace(R.id.BaseFragment, alterData).addToBackStack(null);
                ft.commit();
            }
        });
    }

    //===========
    //teste reduzir consumo da bateria do device.
    @Override
    public void onPause() {

        if(getContext() != null)
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mMessageReceiver);

        super.onPause();
    }
    //=========
    /*
    @Override
    public void onStop() {

        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mMessageReceiver);

        super.onStop();
    }*/

    @Override
    public void onDetach() {

        this.listener = null;
        super.onDetach();

    }

    private void progressBarManager(boolean show){

        if(show){
            ProgressBarManager.showProgressBar(getActivity());
        }else {
            ProgressBarManager.hideProgressBar();
        }

    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        fragmentCallback = (FragmentCallback) getActivity();
        fragmentCallback.onAttach("OrderHistory");
    }

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            if (Home.fragmentName.equals("OrderHistory")) {

                progressBarManager(true);

                if (intent.getExtras() != null) {

                    playNotificationSound();

                    tableIdPush = intent.getExtras().getString("tableId");

                    if (requestServerTask != null)
                        requestServerTask.cancel(true);

                    requestServerTask = new RequestServerTask();
                    requestServerTask.execute(true);

                } else {//usado para o retorno da conexão com a internet, atualizar as ordens.

                    if (requestServerTask != null)
                        requestServerTask.cancel(true);

                    requestServerTask = new RequestServerTask();
                    requestServerTask.execute(false);
                }
            }
        }
    };

    private void showDetailOrder(Order order) {

        progressBarManager(true);

        try {

            BigInteger OrderId = order.getId();
            Bundle bundle = new Bundle();
            bundle.putString("OrderId", OrderId.toString());

            //estava ocorrendo erros na suspensão do App no WiewOrderItem com Serializable, estou recuperando a Ordem do sqlite pelo Id.
            //  bundle.putSerializable("Order2", order);

            FragmentManager fragmentManager = getFragmentManager();
            FragmentTransaction ft = fragmentManager.beginTransaction();
            ft.setCustomAnimations(R.anim.enter_from_left, R.anim.exit_to_right, R.anim.enter_from_right, R.anim.exit_to_left);

            ViewOrderItemController orderItemFragment = new ViewOrderItemController();
            orderItemFragment.setListener(OrderHistory.this);
            orderItemFragment.setArguments(bundle);

            ft.replace(R.id.BaseFragment, orderItemFragment).addToBackStack(null);
            ft.commit();

            //((Home)getActivity()).hideButtonCenter();

        } catch (Exception ex) {
            setError(ex.getMessage());
        }

    }

    @Override
    public void onDestroy() {
        //LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mMessageReceiver);

        if (requestServerTask != null)
            requestServerTask.cancel(true);

        super.onDestroy();
    }

    void setError(String Message) {

        progressBarManager(false);

        SetErrorMessageFail.setError(getActivity(), Message);

    }

    private void playNotificationSound() {


            if(Util.configModel !=null) {
            if (Util.configModel.isSoundNotification()) {

                try {

                    SoundManager.playNotificationSound(getContext());

                } catch (Exception e) {

                    setError(e.getMessage());
                }
            }
        }
    }

    private class RequestServerTask extends AsyncTask<Boolean, Boolean, Boolean> {

        protected Boolean doInBackground(Boolean... push) {
           boolean push2 = push[0];

            OrderController OrderCtrl = new OrderController(getContext());

            Util util = Util.getInstance(getContext());

            if(isConnectedInternet()) {

                if (push2) {

                    if (tableIdPush != null) {

                        BigInteger bigTableId = new BigInteger(tableIdPush);
                        List<Order> orderList = OrderCtrl.Edit(bigTableId);

                        if(orderList != null) {
                            util.EditOrder(orderList.get(0));
                        }

                    }

                } else {

                    List<StatusRecord> statusRecordL = OrderCtrl.VerifyStatusRecord();

                    if (statusRecordL != null) {
                        if (statusRecordL.size() > 0) {

                            if (statusRecordL.get(0).getTable().equals("Order")) {

                                BigInteger id = statusRecordL.get(0).getTableId();
                                List<Order> orderList = OrderCtrl.Edit(id);

                                if(orderList != null) {
                                    util.EditOrder(orderList.get(0));
                                }

                            }
                        }
                    }
                }
            }

            long totalRegistros = util.countOrders();

            OrderList = null;

            if (totalRegistros > 0) {

                List<Order> Orders = util.getOrderList();

                Config ConfigModel = util.getConfig();//RI: atualizar config para filtrar ordens.
                OrderList = util.getFilterOrders3(Orders, ConfigModel);

                if (util.countNSU() > 0) {
                    nsuList = util.getNSUList();
                    nsuContains = true;
                } else {
                    nsuContains = false;
                }
            }

            if (OrderList != null) {

                return OrderList.size() > 0;//boolean

            } else {

                return false;

            }
        }

        protected void onPostExecute(Boolean result) {

            populateListView2(result);

        }
    }

    private void populateListView2(boolean result) {

        if((result)&&(OrderList != null)){

            ArrayList<Order> arrlistofOptions = new ArrayList<Order>(OrderList);//OrderL);

            OrderRecyclerAdapter adapter = null;
            //specify an adapter (see also next example)
            adapter = new OrderRecyclerAdapter(getContext(), arrlistofOptions, new OrderRecyclerAdapter.OnItemClickListener() {
                @Override
                public void onItemClick(Order order) {
                    showDetailOrder(order);
                }
            });

            adapter.setOnBottomReachedListener(new OrderRecyclerAdapter.OnBottomReachedListener() {
                @Override
                public void onBottomReached() {

                    if (nsuContains) {

                        boolean stackSearch = false;
                        for (int a = 0; a < nsuList.size() && !stackSearch; a++) {

                            nsu nsutable = nsuList.get(a);
                            if (nsutable != null) {

                                stackSearch = true;
                                String numberNSU = nsutable.getNSUNumber();
                                nsuList.set(a, null);

                                if(isConnectedInternet()) {
                                    if (!numberNSU.isEmpty()) {

                                        NSUTableController nsuCtrl = new NSUTableController(getActivity());
                                        List<Order> orderList = nsuCtrl.Show(numberNSU, new ReturnOrder());

                                        if (orderList != null) {

                                            if (orderList.size() > 0) {

                                                Util util = Util.getInstance(getContext());
                                                util.OrderListManager(orderList, false);
                                                util.deleteNsu(numberNSU);

                                                long totalRegistros2 = util.countNSU();

                                                if (totalRegistros2 == 0) {

                                                    nsuContains = false;
                                                }
                                            }
                                        }
                                    }
                                }
                                requestServerTask = new RequestServerTask();
                                requestServerTask.execute(false);
                            }
                        }
                    }
                }
            });

            OrderListView.setAdapter(adapter);

        }else {
            //loadHomeQrCode();

            ArrayList<Order> arrlistofOptions = new ArrayList<Order>(0);
            OrderRecyclerAdapter adapter = new OrderRecyclerAdapter(getContext(), arrlistofOptions, null) {
            };
            OrderListView.setAdapter(adapter);
        }

        progressBarManager(false);
    }

    //=================
    //atualizar a lista de fichas após o gesto de Swipe para baixo na lista.
    private void refreshOrders(){

        try{

            OrderController controller = new OrderController(getContext());
            Util util = Util.getInstance(getContext());

            AsyncInterface listener = result -> {

                long totalRegistros = util.countOrders();

                OrderList = null;

                if (totalRegistros > 0) {

                    List<Order> Orders = util.getOrderList();

                    Config configModel = util.getConfig();// atualizar config para filtrar ordens.
                    OrderList = util.getFilterOrders3(Orders, configModel);

                    if (util.countNSU() > 0) {
                        nsuList = util.getNSUList();
                        nsuContains = true;
                    } else {
                        nsuContains = false;
                    }
                }

                boolean testList = false;
                if (OrderList != null) {
                    testList = OrderList.size() > 0;
                }

                refreshLayout.setRefreshing(false);
                populateListView2(testList);

            };
            GetOrdersTask task = new GetOrdersTask(controller,util,listener);
            task.execute();

        }catch (Exception e){
            setError(e.getMessage());
        }
    }

}
