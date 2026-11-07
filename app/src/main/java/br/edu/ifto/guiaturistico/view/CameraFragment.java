package br.edu.ifto.guiaturistico.view;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.view.LifecycleCameraController;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Locale;

import br.edu.ifto.guiaturistico.R;
import br.edu.ifto.guiaturistico.model.PontoTuristico;
import br.edu.ifto.guiaturistico.viewmodel.CameraViewModel;
import br.edu.ifto.guiaturistico.viewmodel.MapaViewModel;

/**
 * Tela para tirar fotos.
 *  - Se o usuário estiver dentro do raio de um ponto turístico, a foto é salva naquele ponto.
 *  - Se estiver fora de todos, o app pergunta se ele quer cadastrar um novo ponto turístico
 *    (nome + localização atual + raio) e, depois do cadastro, tira a foto.
 *
 * Suporta localização simulada: se o usuário definiu uma posição no mapa (via crosshair),
 * essa posição é usada em vez do GPS real. Isso permite testar o app no emulador sem
 * precisar alterar as coordenadas do GPS nas configurações do emulador.
 */
public class CameraFragment extends Fragment {

    private static final int CODIGO_PERMISSOES = 2;
    private static final float RAIO_MINIMO = 10;
    private static final float RAIO_MAXIMO = 5000;

    private static final String[] PERMISSOES = {
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    private CameraViewModel viewModel;
    private MapaViewModel mapaViewModel; // compartilhado com MapaFragment para localização simulada
    private LifecycleCameraController cameraController;
    private FusedLocationProviderClient fusedLocationClient;

    private PreviewView previewView;
    private MaterialCardView cardStatus;
    private TextView tvStatusTitulo, tvStatusDetalhe;
    private FloatingActionButton fabCapturar;
    private View vFlash;

    private boolean capturando = false;

    // recebe a posição do GPS a cada segundo e repassa para o ViewModel
    private final LocationListener ouvinteLocalizacao = location -> {
        // só usa GPS real se não houver localização simulada
        if (mapaViewModel.getLocalizacaoSimulada().getValue() == null) {
            viewModel.atualizarLocalizacao(location);
        }
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_camera, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        previewView = view.findViewById(R.id.preview);
        cardStatus = view.findViewById(R.id.cardStatus);
        tvStatusTitulo = view.findViewById(R.id.tvStatusTitulo);
        tvStatusDetalhe = view.findViewById(R.id.tvStatusDetalhe);
        fabCapturar = view.findViewById(R.id.fabCapturar);
        vFlash = view.findViewById(R.id.vFlash);

        viewModel = new ViewModelProvider(this).get(CameraViewModel.class);
        // obtém o MapaViewModel no escopo da Activity (mesmo que o MapaFragment usa)
        mapaViewModel = new ViewModelProvider(requireActivity()).get(MapaViewModel.class);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext());

        // a tela reage sozinha: mudou a posição ou o ponto atual -> atualiza o cartão de status
        viewModel.getLocalizacao().observe(getViewLifecycleOwner(), l -> atualizarStatus());
        viewModel.getPontoAtual().observe(getViewLifecycleOwner(), p -> atualizarStatus());

        // se a localização simulada mudar, atualiza o ViewModel da câmera
        mapaViewModel.getLocalizacaoSimulada().observe(getViewLifecycleOwner(), locSimulada -> {
            if (locSimulada != null) {
                viewModel.atualizarLocalizacao(locSimulada);
            }
        });

        fabCapturar.setOnClickListener(v -> aoClicarEmCapturar());
        atualizarStatus();

        // se já existe uma localização simulada definida, aplica imediatamente
        Location locSimulada = mapaViewModel.getLocalizacaoSimulada().getValue();
        if (locSimulada != null) {
            viewModel.atualizarLocalizacao(locSimulada);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (temPermissoes())
            iniciarCameraEGps();
        else
            pedirPermissoes();
    }

    @Override
    public void onStop() {
        super.onStop();
        // GPS desligado quando a tela não está visível, para economizar bateria
        fusedLocationClient.removeLocationUpdates(ouvinteLocalizacao);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cameraController = null;
    }

    private boolean temPermissoes() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void pedirPermissoes() {
        boolean explicar = shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
                || shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION);
        if (explicar) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.permissao_titulo)
                    .setMessage(R.string.permissao_camera_explicacao)
                    .setCancelable(false)
                    .setPositiveButton(R.string.pedir_permissao,
                            (dialog, which) -> requestPermissions(PERMISSOES, CODIGO_PERMISSOES))
                    .setNegativeButton(R.string.voltar_ao_mapa, (dialog, which) -> voltar())
                    .show();
        } else {
            requestPermissions(PERMISSOES, CODIGO_PERMISSOES);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != CODIGO_PERMISSOES) return;

        if (temPermissoes()) {
            iniciarCameraEGps();
        } else {
            // princípio do slide: não bloquear o usuário, explicar e deixar ele continuar usando o app
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.permissao_negada_titulo)
                    .setMessage(R.string.permissao_negada_camera)
                    .setCancelable(false)
                    .setPositiveButton(R.string.voltar_ao_mapa, (dialog, which) -> voltar())
                    .show();
        }
    }

    // Câmera e gps

    @SuppressLint("MissingPermission") // só é chamado depois de conferir as permissões
    private void iniciarCameraEGps() {
        if (cameraController == null) {
            cameraController = new LifecycleCameraController(requireContext());
            cameraController.bindToLifecycle(getViewLifecycleOwner());
            cameraController.setCameraSelector(CameraSelector.DEFAULT_BACK_CAMERA);
            previewView.setController(cameraController);
        }

        LocationRequest locationRequest =
                new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).build();
        fusedLocationClient.requestLocationUpdates(locationRequest, ouvinteLocalizacao,
                Looper.getMainLooper());
    }

    private void aoClicarEmCapturar() {
        if (capturando) return;

        if (viewModel.getLocalizacao().getValue() == null) {
            Toast.makeText(requireContext(), R.string.aguardando_gps, Toast.LENGTH_SHORT).show();
            return;
        }

        PontoTuristico ponto = viewModel.getPontoAtual().getValue();
        if (ponto != null)
            tirarFoto(ponto);              // dentro do raio de um ponto -> pode fotografar
        else
            perguntarSeQuerCadastrar();    // fora de todos os pontos -> mostra cadastro
    }

    private void tirarFoto(PontoTuristico ponto) {
        Location posicao = viewModel.getLocalizacao().getValue();
        if (cameraController == null || posicao == null) return;

        capturando = true;
        fabCapturar.setEnabled(false);
        efeitoFlash();

        // cada ponto turístico tem sua própria pasta dentro da área de imagens do app
        File pasta = new File(requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "ponto_" + ponto.getId());
        pasta.mkdirs();
        File arquivo = new File(pasta, "foto-" + new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS",
                Locale.getDefault()).format(System.currentTimeMillis()) + ".jpg");

        ImageCapture.OutputFileOptions outputFileOptions =
                new ImageCapture.OutputFileOptions.Builder(arquivo).build();

        cameraController.takePicture(outputFileOptions, ContextCompat.getMainExecutor(requireContext()),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        viewModel.adicionarFoto(ponto.getId(), arquivo.getAbsolutePath(),
                                posicao.getLatitude(), posicao.getLongitude());
                        terminarCaptura();
                        if (getView() == null) return;
                        Snackbar.make(getView(), getString(R.string.foto_salva, ponto.getNome()),
                                        Snackbar.LENGTH_LONG)
                                .setAnchorView(fabCapturar)
                                .setAction(R.string.ver_fotos, v -> abrirFotos(ponto))
                                .show();
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        terminarCaptura();
                        if (isAdded())
                            Toast.makeText(requireContext(), R.string.erro_salvar_foto, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void terminarCaptura() {
        capturando = false;
        if (getView() != null) fabCapturar.setEnabled(true);
    }

    private void efeitoFlash() {
        vFlash.setAlpha(0.8f);
        vFlash.setVisibility(View.VISIBLE);
        vFlash.animate().alpha(0f).setDuration(250)
                .withEndAction(() -> vFlash.setVisibility(View.GONE))
                .start();
    }
    private void perguntarSeQuerCadastrar() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.fora_titulo)
                .setMessage(R.string.fora_pergunta)
                .setPositiveButton(R.string.cadastrar, (dialog, which) -> mostrarFormularioDeCadastro())
                .setNegativeButton(R.string.cancelar, null)
                .show();
    }

    private void mostrarFormularioDeCadastro() {
        Location posicao = viewModel.getLocalizacao().getValue();
        if (posicao == null) return;

        View formulario = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialogo_novo_ponto, null, false);
        TextInputLayout tilNome = formulario.findViewById(R.id.tilNome);
        TextInputLayout tilRaio = formulario.findViewById(R.id.tilRaio);
        EditText etNome = formulario.findViewById(R.id.etNome);
        EditText etRaio = formulario.findViewById(R.id.etRaio);
        TextView tvLocalizacao = formulario.findViewById(R.id.tvLocalizacao);

        tvLocalizacao.setText(getString(R.string.cadastro_localizacao,
                posicao.getLatitude(), posicao.getLongitude(), posicao.getAccuracy()));

        AlertDialog dialogo = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.cadastro_titulo)
                .setView(formulario)
                .setPositiveButton(R.string.cadastrar_e_fotografar, null) // tratado abaixo, para validar
                .setNegativeButton(R.string.cancelar, null)
                .create();

        // o botão só fecha o diálogo se os dados forem válidos
        dialogo.setOnShowListener(d -> dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String nome = etNome.getText().toString().trim();
            String textoRaio = etRaio.getText().toString().trim();

            tilNome.setError(null);
            tilRaio.setError(null);

            if (nome.isEmpty()) {
                tilNome.setError(getString(R.string.erro_nome_vazio));
                return;
            }
            float raio;
            try {
                raio = Float.parseFloat(textoRaio.replace(',', '.'));
            } catch (NumberFormatException e) {
                raio = -1;
            }
            if (raio < RAIO_MINIMO || raio > RAIO_MAXIMO) {
                tilRaio.setError(getString(R.string.erro_raio_invalido, RAIO_MINIMO, RAIO_MAXIMO));
                return;
            }

            PontoTuristico novoPonto = viewModel.cadastrarPonto(nome, raio);
            dialogo.dismiss();
            if (novoPonto != null) {
                Toast.makeText(requireContext(), getString(R.string.ponto_cadastrado, nome),
                        Toast.LENGTH_SHORT).show();
                tirarFoto(novoPonto);
            }
        }));
        dialogo.show();
    }
    private void atualizarStatus() {
        Location posicao = viewModel.getLocalizacao().getValue();
        PontoTuristico ponto = viewModel.getPontoAtual().getValue();

        if (posicao == null) {
            tvStatusTitulo.setText(R.string.status_procurando);
            tvStatusDetalhe.setText(R.string.status_procurando_detalhe);
            cardStatus.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.status_neutro));
            return;
        }

        // indica se está usando localização simulada ou GPS real
        boolean simulada = mapaViewModel.getLocalizacaoSimulada().getValue() != null;
        String precisao = simulada
                ? getString(R.string.localizacao_simulada_ativa,
                        posicao.getLatitude(), posicao.getLongitude())
                : getString(R.string.status_precisao, posicao.getAccuracy());

        if (ponto != null) {
            // DENTRO do raio de um ponto turístico
            tvStatusTitulo.setText(getString(R.string.status_dentro, ponto.getNome()));
            tvStatusDetalhe.setText(getString(R.string.status_dentro_detalhe,
                    formatarDistancia(viewModel.distanciaAte(ponto)),
                    formatarDistancia(ponto.getRaio()),
                    ponto.getFotos().size()) + "\n" + precisao);
            cardStatus.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.status_dentro));
        } else {
            // FORA de todos os pontos turísticos
            tvStatusTitulo.setText(R.string.status_fora);
            PontoTuristico maisProximo = viewModel.getPontoMaisProximo();
            String detalhe = maisProximo == null
                    ? getString(R.string.status_fora_sem_pontos)
                    : getString(R.string.status_fora_detalhe, maisProximo.getNome(),
                            formatarDistancia(viewModel.distanciaAte(maisProximo)));
            tvStatusDetalhe.setText(detalhe + "\n" + precisao);
            cardStatus.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.status_fora));
        }
    }

    private String formatarDistancia(float metros) {
        if (metros < 1000)
            return String.format(Locale.getDefault(), "%.0f m", metros);
        return String.format(Locale.getDefault(), "%.1f km", metros / 1000);
    }

    // ===== NAVEGAÇÃO =====

    private void abrirFotos(PontoTuristico ponto) {
        NavController navController = NavHostFragment.findNavController(this);
        if (navController.getCurrentDestination() == null
                || navController.getCurrentDestination().getId() != R.id.cameraFragment) return;

        Bundle argumentos = new Bundle();
        argumentos.putLong(FotosFragment.ARG_ID_PONTO, ponto.getId());
        argumentos.putString(FotosFragment.ARG_NOME_PONTO, ponto.getNome());
        navController.navigate(R.id.action_camera_para_fotos, argumentos);
    }

    private void voltar() {
        if (isAdded())
            NavHostFragment.findNavController(this).navigateUp();
    }
}
