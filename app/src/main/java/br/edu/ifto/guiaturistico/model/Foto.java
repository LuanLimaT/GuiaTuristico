package br.edu.ifto.guiaturistico.model;

//Uma foto tirada dentro da região de um ponto turístico.

public class Foto {

    private final String caminho;   // arquivo .jpg salvo na pasta do app
    private final long dataHora;    // momento em que a foto foi tirada (milissegundos)
    private final double latitude;  // posição do aparelho no momento da foto
    private final double longitude;

    public Foto(String caminho, long dataHora, double latitude, double longitude) {
        this.caminho = caminho;
        this.dataHora = dataHora;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getCaminho() { return caminho; }
    public long getDataHora() { return dataHora; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
}
