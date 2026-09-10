package app.utils;

import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Carrega imagens de icone a partir de arquivos .ico, .png, .bmp, .gif, .jpg
 * ou de recursos embutidos no classpath.
 *
 * O ImageIO do Java nao entende o formato .ico, por isso este utilitario faz o
 * parse do container ICO e extrai a melhor imagem disponivel (PNG ou DIB/BMP).
 */
public final class IconLoader {

    private static final Logger log = LoggerFactory.getLogger(IconLoader.class);

    private static final int ICON_HEADER_SIZE = 6;
    private static final int ICON_ENTRY_SIZE = 16;
    private static final int ICO_TYPE = 1;
    private static final int PNG_SIGNATURE_0 = 0x89;
    private static final int PNG_SIGNATURE_1 = 0x50;
    private static final int DEFAULT_PREFERRED_SIZE = 32;

    /** Parametros da cor gerada quando nenhum icone e encontrado. */
    private static final float MIN_FALLBACK_SATURATION = 0.55f;
    private static final float SATURATION_RANGE = 0.25f;
    private static final float FALLBACK_BRIGHTNESS = 0.80f;

    private IconLoader() {
    }

    /**
     * Tenta carregar um icone a partir do caminho informado (arquivo ou
     * recurso do classpath). Retorna {@code null} quando nada e encontrado.
     */
    public static Image load(String path) {
        return load(path, DEFAULT_PREFERRED_SIZE);
    }

    /**
     * Carrega o icone escolhendo o tamanho mais adequado para exibicao.
     *
     * @param preferredSize tamanho desejado em pixels (a menor imagem que
     *                      atenda a esse tamanho e usada; se nenhuma atender,
     *                      a maior disponivel e usada).
     */
    public static Image load(String path, int preferredSize) {
        if (path == null || path.isBlank()) {
            return null;
        }

        String trimmed = path.trim();

        byte[] data = readFromFile(trimmed);
        if (data == null) {
            data = readFromClasspath(trimmed);
        }
        if (data == null && !Paths.get(trimmed).isAbsolute()) {
            Path besideJar = Paths.get(System.getProperty("user.dir"), trimmed);
            data = readFromFile(besideJar.toString());
        }
        if (data == null) {
            data = readFromResourceDirectory(trimmed);
        }
        if (data == null) {
            return null;
        }

        return decode(data, trimmed, preferredSize);
    }

    /**
     * Cria um icone solido na cor informada, usado quando nenhum arquivo de
     * icone e encontrado. Existe para que a bandeja nunca fique sem imagem.
     */
    public static Image createSolidIcon(Color color, int size) {
        int lado = size > 0 ? size : DEFAULT_PREFERRED_SIZE;
        BufferedImage image = new BufferedImage(lado, lado, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, lado, lado);
        graphics.dispose();
        return image;
    }

    /**
     * Gera um icone de identificacao visual quando um icone nao e localizado.
     *
     * <p>A cor e derivada do texto informado (por exemplo, o nome do programa),
     * de modo que o resultado seja estavel entre execucoes: o usuario ve sempre
     * a mesma cor.</p>
     */
    public static Image createFallbackIcon(String seed, int size) {
        int hash = seed == null ? 0 : seed.hashCode();

        // Nao usa Math.abs: com Integer.MIN_VALUE o resultado continuaria
        // negativo. O mascaramento resolve e ainda distribui o matiz.
        int hue = (hash & 0x7FFFFFFF) % 360;
        float saturation = MIN_FALLBACK_SATURATION
                + ((hash & 0x1F) / 31f) * SATURATION_RANGE;

        return createSolidIcon(Color.getHSBColor(hue / 360f, saturation, FALLBACK_BRIGHTNESS), size);
    }
    private static byte[] readFromFile(String path) {
        try {
            Path file = Paths.get(path);
            if (Files.isRegularFile(file)) {
                return Files.readAllBytes(file);
            }
        } catch (IOException | RuntimeException e) {
            // Ignora e tenta as proximas estrategias.
        }
        return null;
    }

    private static byte[] readFromClasspath(String resource) {
        String normalized = resource.startsWith("/") ? resource.substring(1) : resource;
        try (InputStream in = IconLoader.class.getClassLoader().getResourceAsStream(normalized)) {
            if (in != null) {
                return in.readAllBytes();
            }
        } catch (IOException e) {
            // Ignora e tenta as proximas estrategias.
        }
        return null;
    }

    private static byte[] readFromResourceDirectory(String resource) {
        String normalized = resource.startsWith("/") ? resource.substring(1) : resource;
        try (InputStream in = IconLoader.class.getClassLoader()
                .getResourceAsStream("resources/" + normalized)) {
            if (in != null) {
                return in.readAllBytes();
            }
        } catch (IOException e) {
            // Ignora e tenta as proximas estrategias.
        }
        return null;
    }

    private static Image decode(byte[] data, String source, int preferredSize) {
        try {
            if (isIco(data)) {
                return decodeIco(data, preferredSize);
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
            if (image != null) {
                return image;
            }
            log.warn("Formato de imagem nao reconhecido: '{}'.", source);
        } catch (IOException | RuntimeException e) {
            log.warn("Nao foi possivel carregar o icone '{}': {}", source, e.getMessage());
        }
        return null;
    }

    private static boolean isIco(byte[] data) {
        return data.length >= ICON_HEADER_SIZE
                && (data[0] & 0xFF) == 0
                && (data[1] & 0xFF) == 0
                && (data[2] & 0xFF) == ICO_TYPE
                && (data[3] & 0xFF) == 0;
    }

    private static Image decodeIco(byte[] data, int preferredSize) {
        int count = readShort(data, 4);
        if (count <= 0) {
            return null;
        }

        int bestWidth = -1;
        int bestOffset = -1;
        int bestSize = -1;

        for (int i = 0; i < count; i++) {
            int entry = ICON_HEADER_SIZE + (i * ICON_ENTRY_SIZE);
            if (entry + ICON_ENTRY_SIZE > data.length) {
                break;
            }

            int width = data[entry] & 0xFF;
            if (width == 0) {
                width = 256;
            }

            int size = readInt(data, entry + 8);
            int offset = readInt(data, entry + 12);

            if (offset < 0 || size <= 0 || offset + size > data.length) {
                continue;
            }
            if (isBetterEntry(width, bestWidth, preferredSize)) {
                bestWidth = width;
                bestOffset = offset;
                bestSize = size;
            }
        }

        if (bestOffset < 0) {
            return null;
        }

        byte[] imageData = new byte[bestSize];
        System.arraycopy(data, bestOffset, imageData, 0, bestSize);

        if (imageData.length > 1
                && (imageData[0] & 0xFF) == PNG_SIGNATURE_0
                && (imageData[1] & 0xFF) == PNG_SIGNATURE_1) {
            try {
                BufferedImage png = ImageIO.read(new ByteArrayInputStream(imageData));
                if (png != null) {
                    return png;
                }
            } catch (IOException e) {
                return null;
            }
        }

        return decodeDib(imageData);
    }

    /**
     * Escolhe a entrada mais adequada: a menor imagem que ainda atenda ao
     * tamanho preferido. Se nenhuma atender, mantem a maior disponivel.
     */
    private static boolean isBetterEntry(int width, int bestWidth, int preferredSize) {
        if (bestWidth < 0) {
            return true;
        }
        boolean candidateFits = width >= preferredSize;
        boolean bestFits = bestWidth >= preferredSize;

        if (candidateFits != bestFits) {
            return candidateFits;
        }
        if (candidateFits) {
            return width < bestWidth;
        }
        return width > bestWidth;
    }

    /**
     * Decodifica a imagem DIB (BITMAPINFOHEADER + pixels) usada dentro de .ico.
     * O header DIB tem a altura dobrada (XOR + mascara AND) e o canal alfa vem
     * da parte RGBA quando presente. Icones antigos sem canal alfa valido usam
     * a mascara AND para definir a transparencia.
     */
    private static Image decodeDib(byte[] data) {
        if (data.length < 40) {
            return null;
        }

        int headerSize = readInt(data, 0);
        if (headerSize < 40 || headerSize >= data.length) {
            return null;
        }

        int width = readInt(data, 4);
        int height = readInt(data, 8) / 2;
        int bitCount = readShort(data, 14);
        int compression = readInt(data, 16);

        if (width <= 0 || height <= 0 || compression != 0) {
            return null;
        }
        if (bitCount != 32 && bitCount != 24 && bitCount != 8 && bitCount != 4 && bitCount != 1) {
            return null;
        }

        int[] pixels = new int[width * height];
        int xorSize;

        if (bitCount == 32) {
            xorSize = width * 4 * height;
            decode32Bpp(data, headerSize, width, height, pixels);
        } else if (bitCount == 24) {
            xorSize = (((width * 3) + 3) / 4 * 4) * height;
            decode24Bpp(data, headerSize, width, height, pixels);
        } else {
            int paletteEntries = readInt(data, 32);
            int maxEntries = 1 << bitCount;
            if (paletteEntries <= 0 || paletteEntries > maxEntries) {
                paletteEntries = maxEntries;
            }
            int paletteOffset = headerSize;
            int dataOffset = paletteOffset + (paletteEntries * 4);
            int packedRowSize = (((width * bitCount) + 31) / 32) * 4;
            xorSize = packedRowSize * height;
            decodePaletted(data, dataOffset, packedRowSize, paletteOffset, paletteEntries,
                    bitCount, width, height, pixels);
        }

        boolean hasAlpha = bitCount == 32 && hasAlphaChannel(pixels);
        if (!hasAlpha) {
            applyAndMask(data, headerSize + xorSize, width, height, pixels);
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, pixels, 0, width);
        return image;
    }

    private static boolean hasAlphaChannel(int[] pixels) {
        for (int pixel : pixels) {
            if ((pixel >>> 24) != 0) {
                return true;
            }
        }
        return false;
    }

    private static void applyAndMask(byte[] data, int maskOffset, int width, int height, int[] pixels) {
        int rowSize = ((width + 31) / 32) * 4;
        if (maskOffset < 0 || maskOffset + (rowSize * height) > data.length) {
            for (int i = 0; i < pixels.length; i++) {
                pixels[i] |= 0xFF000000;
            }
            return;
        }

        for (int y = 0; y < height; y++) {
            int sourceRow = maskOffset + (height - 1 - y) * rowSize;
            for (int x = 0; x < width; x++) {
                int byteIndex = sourceRow + (x / 8);
                int bitIndex = 7 - (x % 8);
                boolean transparent = ((data[byteIndex] >> bitIndex) & 0x01) == 1;
                int index = y * width + x;
                pixels[index] = transparent ? (pixels[index] & 0x00FFFFFF) : (pixels[index] | 0xFF000000);
            }
        }
    }

    private static void decodePaletted(byte[] data, int offset, int rowSize, int paletteOffset,
                                       int paletteEntries, int bitCount, int width, int height,
                                       int[] pixels) {
        int mask = (1 << bitCount) - 1;
        int pixelsPerByte = 8 / bitCount;

        for (int y = 0; y < height; y++) {
            int sourceRow = offset + (height - 1 - y) * rowSize;
            if (sourceRow + rowSize > data.length) {
                return;
            }
            for (int x = 0; x < width; x++) {
                int byteIndex = sourceRow + (x / pixelsPerByte);
                int shift = 8 - bitCount - ((x % pixelsPerByte) * bitCount);
                int paletteIndex = (data[byteIndex] >> shift) & mask;
                pixels[y * width + x] = readPaletteColor(data, paletteOffset, paletteEntries,
                        paletteIndex);
            }
        }
    }

    private static int readPaletteColor(byte[] data, int paletteOffset, int paletteEntries,
                                        int paletteIndex) {
        int colorIndex = paletteIndex < paletteEntries ? paletteIndex : 0;
        int entry = paletteOffset + (colorIndex * 4);
        if (entry + 3 >= data.length) {
            return 0xFF000000;
        }
        int blue = data[entry] & 0xFF;
        int green = data[entry + 1] & 0xFF;
        int red = data[entry + 2] & 0xFF;
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private static void decode32Bpp(byte[] data, int headerSize, int width, int height, int[] pixels) {
        int offset = headerSize;
        int rowSize = width * 4;

        for (int y = 0; y < height; y++) {
            int sourceRow = offset + (height - 1 - y) * rowSize;
            if (sourceRow + rowSize > data.length) {
                return;
            }
            for (int x = 0; x < width; x++) {
                int p = sourceRow + (x * 4);
                int blue = data[p] & 0xFF;
                int green = data[p + 1] & 0xFF;
                int red = data[p + 2] & 0xFF;
                int alpha = data[p + 3] & 0xFF;
                pixels[y * width + x] = (alpha << 24) | (red << 16) | (green << 8) | blue;
            }
        }
    }

    private static void decode24Bpp(byte[] data, int headerSize, int width, int height, int[] pixels) {
        int offset = headerSize;
        int rowSize = ((width * 3) + 3) / 4 * 4;

        for (int y = 0; y < height; y++) {
            int sourceRow = offset + (height - 1 - y) * rowSize;
            if (sourceRow + rowSize > data.length) {
                return;
            }
            for (int x = 0; x < width; x++) {
                int p = sourceRow + (x * 3);
                int blue = data[p] & 0xFF;
                int green = data[p + 1] & 0xFF;
                int red = data[p + 2] & 0xFF;
                pixels[y * width + x] = 0xFF000000 | (red << 16) | (green << 8) | blue;
            }
        }
    }

    private static int readShort(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static int readInt(byte[] data, int offset) {
        return (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16)
                | ((data[offset + 3] & 0xFF) << 24);
    }
}
