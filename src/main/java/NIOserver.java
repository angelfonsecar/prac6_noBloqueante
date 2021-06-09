import org.apache.commons.io.FilenameUtils;
import org.zeroturnaround.zip.ZipUtil;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

public class NIOserver {
    private String dirActual;
    private String raiz;
    private Selector selector;

    public NIOserver() {
        try{
            File f = new File("");
            String ruta = f.getAbsolutePath();
            String carpeta="drive";
            raiz = ruta+"\\"+carpeta+"\\";
            dirActual = raiz;
            System.out.println("ruta:"+raiz);
            File f2 = new File(raiz);
            f2.mkdirs();
            f2.setWritable(true);

            selector = Selector.open();
            ServerSocketChannel server = ServerSocketChannel.open();
            server.configureBlocking(false);
            server.setOption(StandardSocketOptions.SO_REUSEADDR,true);
            server.socket().bind(new InetSocketAddress("127.0.0.1", 3500));
            server.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("Servidor iniciado esperando por archivos...");

            for(;;){

                selector.select();
                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();

                while (iterator.hasNext()) {
                    SelectionKey key = (SelectionKey) iterator.next();
                    iterator.remove();

                    if (key.isAcceptable()) {
                        SocketChannel client = server.accept();
                        System.out.println("Cliente conectado desde" +client.socket().getInetAddress( )+ ":" +client.socket( ).getPort( ));
                        client.configureBlocking(false);
                        client.register(selector, SelectionKey.OP_WRITE| SelectionKey.OP_READ);
                        continue;
                    }

                    SocketChannel channel = (SocketChannel) key.channel();

                    if(key.isWritable()){
                        mostrarArchivos(channel);
                        key.interestOps(SelectionKey.OP_READ);
                    }
                    else if(key.isReadable()){
                        ByteBuffer bb = ByteBuffer.allocate(4);
                        bb.clear();
                        channel.read(bb);
                        bb.flip();
                        int elec = bb.getInt();
                        System.out.println("elec = " + elec);
                        if(elec==0) {
                            channel.close();
                            break;
                        }
                        switch (elec) {
                            case 1 -> {   //subir archivo o carpeta
                                subir(channel);
                                mostrarArchivos(channel);
                            }
                            case 2 -> {
                                descargar(channel);
                            }
                            case 3 -> {
                                eliminar(channel);
                                mostrarArchivos(channel);
                            }
                            case 4 -> {
                                System.out.println("cambiar dir");
                                cambiarDir(channel);
                            }
                        }
                    }
                }
            }

        }catch(Exception e){
            e.printStackTrace();
        }
    }

    private void escribeObjeto(Object o, SocketChannel channel) throws IOException {    //Envía un objeto al canal
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos2 = new ObjectOutputStream(baos);
        oos2.writeObject(o);
        oos2.flush();
        byte[] b = baos.toByteArray();
        ByteBuffer buffer = ByteBuffer.wrap(b);
        channel.write(buffer);
        oos2.close();
        baos.close();
    }
    private Object leeObjeto(SocketChannel client) throws IOException, ClassNotFoundException {
        ByteBuffer b = ByteBuffer.allocate(2000);
        b.clear();
        client.read(b);
        b.flip();
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(b.array()));
        Object o = ois.readObject();
        ois.close();
        return o;
    }

    public void esperaParaLeer(SocketChannel channel) throws IOException {
        while (true){
            selector.select();
            Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();

            while (iterator.hasNext()) {
                SelectionKey key = (SelectionKey) iterator.next();
                iterator.remove();

                if(!key.channel().equals(channel))  //si el canal relacionado a la key no es el mismo desde el que se
                    continue;                       //invocó la función, buscar otra llave

                if(key.isWritable()) {
                    key.interestOps(SelectionKey.OP_READ);
                }
                if(key.isReadable()) {
                    return;
                }
            }
        }
    }

    public void mostrarArchivos(SocketChannel channel) throws IOException {
        File f = new File(dirActual);
        File []listaDeArchivos = f.listFiles();
        escribeObjeto(listaDeArchivos,channel);
    }

    public void subir(SocketChannel channel) throws IOException, ClassNotFoundException {
        esperaParaLeer(channel);
        File f = (File)leeObjeto(channel);
        String fileName = f.toString();
        String aux = FilenameUtils.getExtension(fileName);

        if(aux.equals("zip")) {
            System.out.println("\nEl cliente quiere subir carpeta");
            subirDir(f,channel);
        }
        else {
            System.out.println("\nEl cliente quiere subir un archivo");
            subirArchivo(f,channel);
        }
    }

    public void subirDir(File f, SocketChannel channel) throws IOException{
        System.out.println("carpeta a subir:"+f.getName());
        String destino = Paths.get(dirActual, f.getName()).toString();
        subirArchivo(f,channel);
        Path descom = Paths.get(dirActual, FilenameUtils.removeExtension(f.getName()) );
        System.out.println("Descomprimiendo " + destino + " en " + descom);
        new net.lingala.zip4j.ZipFile(destino).extractAll(descom.toString());
        new File(destino).delete();
    }

    public void subirArchivo(File f, SocketChannel channel) throws IOException {
        long tam = f.length();
        System.out.println("Comienza descarga del archivo '"+f.getName()+"' de "+tam/1024+" kb");
        System.out.println("Subiendo a "+dirActual);
        DataOutputStream dosf = new DataOutputStream(new FileOutputStream(dirActual+f.getName()));

        long recibidos=0;
        int l;
        while(recibidos<tam){
            ByteBuffer b = ByteBuffer.allocate(1500);
            b.clear();
            //bloquearse hasta que el client haya escrito
            esperaParaLeer(channel);
            channel.read(b);
            b.flip();
            l = b.limit();
            dosf.write(b.array(),0,l);
            dosf.flush();
            escribeObjeto("",channel);
            recibidos += l;
        }
        System.out.println("Archivo recibido");
        dosf.close();
    }

    public void descargar(SocketChannel channel) throws IOException, ClassNotFoundException{
        esperaParaLeer(channel);
        String elecdow = (String) leeObjeto(channel);
        File fileToDowload = new File(dirActual+"\\"+elecdow);
        if(fileToDowload.exists()){
            System.out.println("Descargando "+fileToDowload.getAbsolutePath());
            escribeObjeto("Descargando"+fileToDowload.getAbsolutePath(),channel);
            if (!fileToDowload.isDirectory()) {  //descargar archivo
                enviarDeseado(fileToDowload,channel);
            } else {
                String nomzip = fileToDowload.getAbsolutePath()+".zip";
                System.out.println("nombre:" +nomzip);
                ZipUtil.pack(fileToDowload, new File(nomzip));
                enviarDeseado(new File(nomzip),channel);
                new File(nomzip).delete();
            }
        }else {
            escribeObjeto("El archivo o dir no existe",channel);
        }
    }

    public void enviarDeseado(File f,SocketChannel channel) throws IOException, ClassNotFoundException {
        long tam = f.length();
        System.out.println("Enviando '"+f.getName()+"' de "+tam/1024+" kb");
        esperaParaLeer(channel);
        leeObjeto(channel);
        escribeObjeto(f,channel);
        DataInputStream disf = new DataInputStream(new FileInputStream(f.getAbsolutePath()));
        long enviados = 0;
        int l;
        while (enviados<tam){
            byte[] b = new byte[1500];
            l=disf.read(b);
            ByteBuffer buffer = ByteBuffer.wrap(b);
            channel.write(buffer);
            esperaParaLeer(channel);
            leeObjeto(channel);
            enviados += l;
        }
        disf.close();
        System.out.println("Archivo enviado");
    }

    public void eliminar(SocketChannel channel) throws IOException, ClassNotFoundException {
        esperaParaLeer(channel);
        String elec = (String) leeObjeto(channel);
        File fileToDelete = new File(dirActual+"\\"+elec);
        if(fileToDelete.exists()){
            System.out.println("eliminando "+fileToDelete.getAbsolutePath());
            if (!fileToDelete.isDirectory()) {  //eliminar archivo
                if(fileToDelete.delete()) {
                    escribeObjeto("Archivo eliminado", channel);
                }
                else {
                    escribeObjeto("Error al eliminar archivo", channel);
                }
            }
            else {        //eliminar directorio
                if(deleteDirectory(fileToDelete)) {
                    escribeObjeto("Dir eliminado", channel);
                }
                else {
                    escribeObjeto("Error al eliminar dir", channel);
                }
            }
        }else {
            escribeObjeto("El archivo o dir no existe",channel);
        }
    }
    boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents)
                deleteDirectory(file);
        }
        return directoryToBeDeleted.delete();
    }

    public void cambiarDir(SocketChannel channel) throws IOException, ClassNotFoundException {
        esperaParaLeer(channel);
        String elec = (String) leeObjeto(channel);
        File f = new File(dirActual+"\\"+elec);
        boolean existeDir = f.isDirectory() || elec.equals("..");
        if(!existeDir){  //comprobamos que el directorio solicitado existe
            elec = "";
            System.out.println("Dir invalido");
        }
        if (existeDir)
            escribeObjeto("si",channel);
        else
            escribeObjeto("no",channel);

        System.out.println("Entrar a '"+elec+"'");
        if(elec.equals("..")){
            dirActual = raiz;
        }else if(!elec.equals("")){
            dirActual = dirActual + elec + "\\";
        }
        mostrarArchivos(channel);
    }

    public static void main(String[] args) {
        new NIOserver();
    }
}
