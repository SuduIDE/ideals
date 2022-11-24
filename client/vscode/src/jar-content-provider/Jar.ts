export const JAR_CONTENT_SCHEME = "jar";
export const ZIP_CONTENT_SCHEME = "zip";

export const JAR_CONTENT_SEPARATOR = '!';

export const PATH_SEPARATOR = '/';
export const PACKAGE_SEPARATOR = '.';

interface JarTuple {
	base: string
	path: string
}

/**
 * Splits a path at the Jar content separator '!' into the base jar file path
 * and the content file path.
 * 
 * @param jarPath - path to be split
 * @returns tuple containing the base and content file path
 */
export function split(jarPath: string): JarTuple {
	const regex = `(.*)${JAR_CONTENT_SEPARATOR}+(.*)`;
	const [_, base, path] = new RegExp(regex).exec(jarPath) ?? [];
	return base ? { base, path } : { base: jarPath, path: '' };
}

/**
 * Removes leading slashes '/' from path
 * 
 * @param path - path to be trimmed
 * @returns path not containing leading '/'
 */
export function trim(path: string): string {
    const regex = `^${PATH_SEPARATOR}*(.*)`;
	const [_, result] = new RegExp(regex).exec(path) ?? [];
	return result;
}
