package br.edu.ifto.guiaturistico.model;

import android.location.Location;

import java.util.ArrayList;
import java.util.List;

/**
 * Um ponto turístico é formado por um nome, uma localização (o centro) e um raio.
 * Tudo o que estiver a até "raio" metros do centro faz parte do ponto turístico.
 */
public class PontoTuristico {

    private final long id;
    private final String nome;
    private final double latitude;
    private final double longitude;
    private final float raio;                        // em metros
    private final List<Foto> fotos = new ArrayList<>();

    public PontoTuristico(long id, String nome, double latitude, double longitude, float raio) {
        this.id = id;
        this.nome = nome;
        this.latitude = latitude;
        this.longitude = longitude;
        this.raio = raio;
    }

    //Distância em metros entre o centro deste ponto e uma posição qualquer
    public float distanciaAte(double outraLatitude, double outraLongitude) {
        float[] resultado = new float[1];
        Location.distanceBetween(latitude, longitude, outraLatitude, outraLongitude, resultado);
        return resultado[0];
    }

    //true se a posição informada está dentro do raio deste ponto
    public boolean contem(double outraLatitude, double outraLongitude) {
        return distanciaAte(outraLatitude, outraLongitude) <= raio;
    }

    public long getId() { return id; }
    public String getNome() { return nome; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public float getRaio() { return raio; }
    public List<Foto> getFotos() { return fotos; }
}
